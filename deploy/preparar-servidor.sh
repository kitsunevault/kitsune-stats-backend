#!/usr/bin/env bash
# Prepara el servidor de Kitsune Vaults para Kitsune Stats. Lo corre solo el primer
# despliegue (.github/workflows/deploy.yml, si aún no existe /etc/kitsune/stats.env);
# a mano sería:
#
#   sudo bash preparar-servidor.sh <admins: correos o usuarioIds de la cuenta Kitsune, separados por coma>
#
# Junto a este script: stats-service.service y nginx-stats.conf.
# Volver a correrlo no rompe nada: lo que ya existe se deja igual.
#
# Qué hace:
#   1. JWT_APPS_SECRET en /etc/kitsune/kitsune.env (users-service firma con él los
#      tokens de Stats; lo toma en su próximo reinicio). Se genera aquí; nunca sale del servidor.
#   2. Rol y base kitsune_stats (contraseña generada aquí; nadie más se conecta a esa base).
#   3. /etc/kitsune/stats.env con la contraseña, el secreto de apps, el secreto interno y los admins.
#   4. Unidad systemd stats-service (habilitada; el despliegue la arranca).
#   5. nginx para stats.kitsunevaults.com.
set -euo pipefail

ADMINS_ENTRADA="${1:-}"
AQUI="$(cd "$(dirname "$0")" && pwd)"
ENV_KITSUNE=/etc/kitsune/kitsune.env
ENV_STATS=/etc/kitsune/stats.env
PSQL=(sudo -u postgres psql -X -v ON_ERROR_STOP=1 -q)

[ "$(id -u)" -eq 0 ] || { echo "Córrelo con sudo." >&2; exit 1; }
for f in "$ENV_KITSUNE" /etc/nginx/proxy_params_kitsune /etc/ssl/cloudflare/kitsunevaults.com.pem \
         "$AQUI/stats-service.service" "$AQUI/nginx-stats.conf"; do
  [ -e "$f" ] || { echo "Falta $f" >&2; exit 1; }
done
cd /tmp

valor() { grep -E "^$1=" "$ENV_KITSUNE" | tail -1 | cut -d= -f2- || true; }

# 1. Secreto de apps para users-service.
if [ -z "$(valor JWT_APPS_SECRET)" ]; then
  cp -p "$ENV_KITSUNE" "$ENV_KITSUNE.respaldo-$(date +%Y%m%d-%H%M%S)"
  [ -z "$(tail -c1 "$ENV_KITSUNE")" ] || echo >> "$ENV_KITSUNE"
  echo "JWT_APPS_SECRET=$(openssl rand -hex 48)" >> "$ENV_KITSUNE"
  echo "✓ JWT_APPS_SECRET agregado a $ENV_KITSUNE"
else
  echo "· JWT_APPS_SECRET ya estaba en $ENV_KITSUNE"
fi
SECRETO_APPS="$(valor JWT_APPS_SECRET)"
SECRETO_INTERNO="$(valor INTERNAL_SERVICE_SECRET)"
[ -n "$SECRETO_INTERNO" ] || { echo "INTERNAL_SERVICE_SECRET no está en $ENV_KITSUNE" >&2; exit 1; }

# Admins: un correo se busca en las cuentas Kitsune (kitsune_platform.usuarios).
ADMINS=""
IFS=',' read -ra lista <<< "$ADMINS_ENTRADA"
for a in "${lista[@]}"; do
  a="$(echo "$a" | tr -d '[:space:]')"
  [ -n "$a" ] || continue
  if [[ "$a" =~ ^[0-9]+$ ]]; then
    id="$a"
  elif [[ "$a" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]]; then
    id="$("${PSQL[@]}" -d kitsune_platform -tAc "SELECT id FROM usuarios WHERE lower(email) = lower('$a') ORDER BY id LIMIT 1")"
    [ -n "$id" ] || { echo "AVISO: no hay cuenta Kitsune con el correo $a; no queda como admin." >&2; continue; }
  else
    echo "AVISO: admin inválido '$a' (correo o número)." >&2
    continue
  fi
  ADMINS="${ADMINS:+$ADMINS,}$id"
done
echo "· Admins de Stats (usuarioId): ${ADMINS:-ninguno}"

# 2. Rol y base. La contraseña se reusa si stats.env ya existe.
if [ -f "$ENV_STATS" ] && grep -q '^STATS_DB_PASSWORD=' "$ENV_STATS"; then
  CLAVE_DB="$(grep '^STATS_DB_PASSWORD=' "$ENV_STATS" | cut -d= -f2-)"
else
  CLAVE_DB="$(openssl rand -hex 24)"
fi
if [ "$("${PSQL[@]}" -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'kitsune_stats'")" != "1" ]; then
  "${PSQL[@]}" -c "CREATE ROLE kitsune_stats LOGIN CONNECTION LIMIT 8 PASSWORD '$CLAVE_DB'"
  echo "✓ Rol kitsune_stats creado"
else
  "${PSQL[@]}" -c "ALTER ROLE kitsune_stats PASSWORD '$CLAVE_DB'"
  echo "· Rol kitsune_stats ya existía (contraseña sincronizada)"
fi
if [ "$("${PSQL[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname = 'kitsune_stats'")" != "1" ]; then
  "${PSQL[@]}" -c "CREATE DATABASE kitsune_stats OWNER kitsune_stats"
  echo "✓ Base kitsune_stats creada"
else
  echo "· Base kitsune_stats ya existía"
fi
# Nadie más se conecta a esta base. kitsune_stats no tiene permisos sobre las tablas de las tiendas.
"${PSQL[@]}" -c "REVOKE CONNECT ON DATABASE kitsune_stats FROM PUBLIC"

# 3. Variables de stats-service (solo root).
umask 077
cat > "$ENV_STATS" <<EOF
STATS_DB_USER=kitsune_stats
STATS_DB_PASSWORD=$CLAVE_DB
JWT_APPS_SECRET=$SECRETO_APPS
INTERNAL_SERVICE_SECRET=$SECRETO_INTERNO
STATS_ADMINS=$ADMINS
EOF
chmod 600 "$ENV_STATS"
umask 022
echo "✓ $ENV_STATS escrito"

# 4. systemd.
install -m 644 "$AQUI/stats-service.service" /etc/systemd/system/stats-service.service
systemctl daemon-reload
systemctl enable stats-service >/dev/null 2>&1
echo "✓ Unidad stats-service instalada y habilitada"

# 5. nginx. Si la configuración no pasa, se quita para no dejar nginx sin poder recargar.
mkdir -p /var/www/kitsune-stats
chown www-data:www-data /var/www/kitsune-stats
install -m 644 "$AQUI/nginx-stats.conf" /etc/nginx/sites-available/kitsune-stats
ln -sf /etc/nginx/sites-available/kitsune-stats /etc/nginx/sites-enabled/kitsune-stats
if ! nginx -t 2>&1; then
  rm -f /etc/nginx/sites-enabled/kitsune-stats
  echo "nginx rechazó la configuración de Stats; se quitó y nginx sigue como estaba." >&2
  exit 1
fi
systemctl reload nginx
echo "✓ nginx: stats.kitsunevaults.com"

echo
echo "Memoria del servidor:"
free -m
