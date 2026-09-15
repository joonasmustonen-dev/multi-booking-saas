#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
docker compose exec -T postgres pg_dumpall --username postgres \
  | gzip > "backups/all-databases-$stamp.sql.gz"
find backups -type f -name '*.sql.gz' -mtime +7 -delete
echo "Created backups/all-databases-$stamp.sql.gz"
