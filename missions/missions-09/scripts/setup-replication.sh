#!/usr/bin/env bash
set -euo pipefail

PRIMARY_CONTAINER="${PRIMARY_CONTAINER:-shortener-db-primary-mission-09}"
REPLICA_CONTAINER="${REPLICA_CONTAINER:-shortener-db-replica-mission-09}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"

mysql_exec() {
  local container="$1"
  local sql="$2"
  docker exec "$container" mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -e "$sql"
}

echo "Ensure replication user exists on primary"
mysql_exec "$PRIMARY_CONTAINER" "
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
"

echo "Configure replica with GTID auto-position"
docker exec "$REPLICA_CONTAINER" mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -e "STOP REPLICA;" >/dev/null 2>&1 || true
docker exec "$REPLICA_CONTAINER" mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -e "RESET REPLICA ALL;" >/dev/null 2>&1 || true

mysql_exec "$REPLICA_CONTAINER" "
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='db-primary',
  SOURCE_PORT=3306,
  SOURCE_USER='repl',
  SOURCE_PASSWORD='repl',
  SOURCE_AUTO_POSITION=1,
  GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
"

echo "Replica status"
mysql_exec "$REPLICA_CONTAINER" "SHOW REPLICA STATUS\\G"
