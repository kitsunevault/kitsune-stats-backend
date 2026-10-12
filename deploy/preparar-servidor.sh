#!/usr/bin/env bash
# Prepara el servidor de Kitsune Vaults para Kitsune Stats. Se corre UNA vez, antes
# del primer despliegue (y antes de subir el users-service con las apps OAuth):
#
#   sudo bash preparar-servidor.sh <usuarioIds admin separados por coma>
#
# Copiar antes a la misma carpeta: stats-service.service y nginx-stats.conf.
# Volver a correrlo no rompe nada: lo que ya existe se deja igual.
#
# Qué hace:
#   1. JWT_APPS_SECRET en /etc/kitsune/kitsune.env (users-service firma con él los
#      tokens de Stats). Se genera aquí; nunca sale del servidor.
#   2. Rol y base kitsune_stats (contraseña generada aquí, solo puede conectarse a su base).
#   3. /etc/kitsune/stats.env con la contraseña, el secreto de apps y el secreto interno.
#   4. Unidad systemd stats-service (habilitada; arranca con el primer despliegue).
#   5. nginx para stats.kitsunevaults.com.
set -euo pipefail

ADMINS="${1:?Uso: sudo bash preparar-servidor.sh <usuarioIds admin, p. ej. 1>}"
AQUI="$(cd "$(dirname "$0")" && pwd)"
ENV_KITSUNE=/etc/kitsune/kitsune.env
ENV_STATS=/etc/kitsune/stats.env

[ "$(id -u)" -eq 0 ] || { echo "Córrelo con sudo." >&2; exit 1; }
for f in "$ENV_KITSUNE" /etc/nginx/proxy_params_kitsune /etc/letsencrypt/live/kitsunevaults.com/fullchain.pem \
         "$AQUI/stats-service.service" "$AQUI/nginx-stats.conf"; do
  [ -e "$f" ] || { echo "Falta $f" >&2; exit 1; }
done

valor() { grep -E "^$1=" "$ENV_KITSUNE" | tail -1 | cut -d= -f2- || true; }

# 1. Secreto de apps para users-service.
if [ -z "$(valor JWT_APPS_SECRET)" ]; then
  cp -p "$ENV_KITSUNE" "$ENV_KITSUNE.respaldo-$(date +%Y%m%d-%H%M%S)"
  echo "JWT_APPS_SECRET=$(openssl rand -hex 48)" >> "$ENV_KITSUNE"
  echo "✓ JWT_APPS_SECRET agregado a $ENV_KITSUNE (users-service lo toma en su próximo reinicio)"
else
  echo "· JWT_APPS_SECRET ya estaba en $ENV_KITSUNE"
fi
SECRETO_APPS="$(valor JWT_APPS_SECRET)"
SECRETO_INTERNO="$(valor INTERNAL_SERVICE_SECRET)"
[ -n "$SECRETO_INTERNO" ] || { echo "INTERNAL_SERVICE_SECRET no está en $ENV_KITSUNE" >&2; exit 1; }

# 2. Rol y base. La contraseña se reusa si stats.env ya existe.
if [ -f "$ENV_STATS" ] && grep -q '^STATS_DB_PASSWORD=' "$ENV_STATS"; then
  CLAVE_DB="$(grep '^STATS_DB_PASSWORD=' "$ENV_STATS" | cut -d= -f2-)"
else
  CLAVE_DB="$(openssl rand -hex 24)"
fi
PSQL=(sudo -u postgres psql -X -v ON_ERROR_STOP=1 -q)
if [ "$("${PSQL[@]}" -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'kitsune_stats'")" != "1" ]; then
  "${PSQL[@]}" -c "CREATE ROLE kitsune_stats LOGIN CONNECTION LIMIT 8 PASSWORD '$CLAVE_DB'"
  echo "✓ Rol kitsune_stats creado"
else
  "${PSQL[@]}" -c "ALTER ROLE kitsune_stats PASSWORD '$CLAVE_DB'"
  echo "· Rol kitsune_stats ya existía (contraseña sincronizada con $ENV_STATS)"
fi
if [ "$("${PSQL[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname = 'kitsune_stats'")" != "1" ]; then
  "${PSQL[@]}" -c "CREATE DATABASE kitsune_stats OWNER kitsune_stats"
  echo "✓ Base kitsune_stats creada"
else
  echo "· Base kitsune_stats ya existía"
fi
# Nadie más se conecta a esta base. kitsune_stats no tiene permisos sobre las tablas de las tiendas.
"${PSQL[@]}" -c "REVOKE CONNECT ON DATABASE kitsune_stats FROM PUBLIC"

# 3. Variables de stats-service.
umask 077
cat > "$ENV_STATS" <<EOF
STATS_DB_USER=kitsune_stats
STATS_DB_PASSWORD=$CLAVE_DB
JWT_APPS_SECRET=$SECRETO_APPS
INTERNAL_SERVICE_SECRET=$SECRETO_INTERNO
STATS_ADMINS=$ADMINS
EOF
chmod 600 "$ENV_STATS"
echo "✓ $ENV_STATS escrito (solo root)"

# 4. systemd.
install -m 644 "$AQUI/stats-service.service" /etc/systemd/system/stats-service.service
systemctl daemon-reload
systemctl enable stats-service >/dev/null 2>&1
echo "✓ Unidad stats-service instalada y habilitada"

# 5. nginx.
mkdir -p /var/www/kitsune-stats
chown www-data:www-data /var/www/kitsune-stats
install -m 644 "$AQUI/nginx-stats.conf" /etc/nginx/sites-available/kitsune-stats
ln -sf /etc/nginx/sites-available/kitsune-stats /etc/nginx/sites-enabled/kitsune-stats
nginx -t
systemctl reload nginx
echo "✓ nginx: stats.kitsunevaults.com"

echo
echo "Listo. Sigue: push de KitsuneShop/backend (reinicia users-service con el secreto) y luego push de KitsuneStats."
