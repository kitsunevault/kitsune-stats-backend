#!/usr/bin/env bash
# Aplica las migraciones pendientes de migraciones/*.sql a la base de Kitsune Stats.
#
# La base anota lo aplicado en public.migraciones_aplicadas, así que correrlo dos
# veces no hace nada la segunda. Cada archivo va en una sola transacción junto con
# su anotación: si falla, la base queda como estaba y el script se detiene (el
# despliegue ya no reinicia el servicio).
#
# Lo corre el despliegue de GitHub Actions antes de reiniciar stats-service:
#   sudo -u postgres bash aplicar-migraciones.sh <carpeta migraciones>
# En local: con PGHOST/PGUSER (y pgpass) de un usuario que pueda crear tablas.
set -euo pipefail

DIR="${1:?Uso: aplicar-migraciones.sh <carpeta migraciones>}"
DB="${STATS_DB_NAME:-kitsune_stats}"
# Rol dueño de las tablas (en producción kitsune_stats; en local, el de siempre).
ROL="${STATS_DB_ROLE:-}"
PSQL=(psql -X -v ON_ERROR_STOP=1 -q)
NOMBRE_VALIDO='^[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9-]+\.sql$'
SET_ROLE=""
if [ -n "$ROL" ]; then
  SET_ROLE="SET ROLE $ROL;"
fi

shopt -s nullglob
archivos=("$DIR"/*.sql)
for f in "${archivos[@]}"; do
  if ! [[ "$(basename "$f")" =~ $NOMBRE_VALIDO ]]; then
    echo "Nombre de migración inválido: $(basename "$f") (debe ser AAAA-MM-DD-nombre.sql, minúsculas y guiones)" >&2
    exit 1
  fi
done

"${PSQL[@]}" -d "$DB" -c "SET client_min_messages TO warning; $SET_ROLE CREATE TABLE IF NOT EXISTS public.migraciones_aplicadas (
  archivo text PRIMARY KEY,
  sha256 text NOT NULL,
  aplicada_en timestamptz NOT NULL DEFAULT now()
);" > /dev/null

aplicadas=0
for f in "${archivos[@]}"; do
  nombre=$(basename "$f")
  suma=$(sha256sum "$f" | cut -d' ' -f1)
  previa=$("${PSQL[@]}" -d "$DB" -tAc "SELECT sha256 FROM public.migraciones_aplicadas WHERE archivo = '$nombre'" | tr -d '\r')
  if [ -n "$previa" ]; then
    if [ "$previa" != "$suma" ]; then
      echo "AVISO: $nombre cambió después de aplicarse; no se vuelve a aplicar (los cambios van en una migración nueva)." >&2
    fi
    continue
  fi
  echo "→ $DB: $nombre"
  {
    echo "$SET_ROLE"
    cat "$f"
    echo
    echo "INSERT INTO public.migraciones_aplicadas (archivo, sha256) VALUES ('$nombre', '$suma');"
  } | "${PSQL[@]}" -d "$DB" --single-transaction > /dev/null
  aplicadas=$((aplicadas + 1))
done

echo "Migraciones de Kitsune Stats: $aplicadas aplicadas (${#archivos[@]} en el repo)."
