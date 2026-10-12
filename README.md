# kitsune-stats-backend

stats-service de Kitsune Stats (stats.kitsunevaults.com): Spring Boot 3.4, Java 21, arquitectura
hexagonal, PostgreSQL (`kitsune_stats`). El frontend vive en `kitsune-stats-frontend`.

El login es el de Kitsune Vaults: users-service emite el JWT de Stats (claim `app=stats`, firmado
con `JWT_APPS_SECRET`) y este servicio solo lo valida.

## Local

- `run-local.bat` (puerto 8085, HTTP). Necesita users-service y orders-service de KitsuneShop.
- Admins locales en `config/application.properties` (`stats.admins=<usuarioId>`, no se sube).
- Migraciones: `PGHOST=localhost PGUSER=kitsune_admin bash aplicar-migraciones.sh migraciones`.

## Producción

Cada push a `main` compila, corre las pruebas, aplica `migraciones/` a `kitsune_stats` y reinicia
`stats-service` (`.github/workflows/deploy.yml`). Secrets del repo: `SSH_PRIVATE_KEY`, `SSH_HOST`,
`SSH_USER` (los mismos que los repos de KitsuneShop).

Primera vez, en este orden:

1. En el servidor, con `deploy/` copiado a una carpeta:
   `sudo bash preparar-servidor.sh <usuarioIds admin>`. Crea la base y el rol, los secretos
   (`/etc/kitsune/stats.env` y `JWT_APPS_SECRET` en `kitsune.env`), la unidad de systemd y nginx.
2. Push de KitsuneShop/backend (users-service con las apps OAuth y orders-service con
   `/api/interno/torneos/de-usuario`).
3. Push de este repo y de `kitsune-stats-frontend`.

Salud para el monitor: `https://stats.kitsunevaults.com/salud`. La base entra sola en el
respaldo diario (respalda todas las bases del servidor).

Migraciones nuevas: archivo `migraciones/AAAA-MM-DD-nombre.sql`, SQL puro, nunca editar uno ya
aplicado.
