param(
    [string]$PrimaryContainer = "shortener-db-primary-mission-09",
    [string]$ReplicaContainer = "shortener-db-replica-mission-09",
    [string]$MysqlRootPassword = "root"
)

function Invoke-MySql {
    param(
        [string]$Container,
        [string]$Sql,
        [switch]$IgnoreError
    )

    & docker exec $Container mysql -uroot "-p$MysqlRootPassword" -e $Sql
    if ($LASTEXITCODE -ne 0 -and -not $IgnoreError) {
        throw "mysql command failed on $Container"
    }
}

Write-Host "Ensure replication user exists on primary"
Invoke-MySql $PrimaryContainer @"
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
"@

Write-Host "Configure replica with GTID auto-position"
Invoke-MySql $ReplicaContainer "STOP REPLICA;" -IgnoreError
Invoke-MySql $ReplicaContainer "RESET REPLICA ALL;" -IgnoreError

Invoke-MySql $ReplicaContainer @"
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='db-primary',
  SOURCE_PORT=3306,
  SOURCE_USER='repl',
  SOURCE_PASSWORD='repl',
  SOURCE_AUTO_POSITION=1,
  GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
"@

Write-Host "Replica status"
Invoke-MySql $ReplicaContainer "SHOW REPLICA STATUS\G"
