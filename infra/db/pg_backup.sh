#!/bin/bash

DB_URI="postgresql://localhost/chickens?user=chicken_user&password="

BACKUP_DIR=/srv/chickens/backups/
BACKUP_FILE=$BACKUP_DIR"`date +\%Y-\%m-\%d`.sql"

pg_dump -Fp "$DB_URI" | gzip > $BACKUP_FILE.gz
