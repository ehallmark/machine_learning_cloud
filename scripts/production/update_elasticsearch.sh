#!/usr/bin/env bash

# update
cd /home/ehallmark/repos/machine_learning_cloud
source scripts/production/backup.sh

curl -XDELETE localhost:9200/big_query
sleep 5
java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.elasticsearch.CreatePatentIndex
sleep 5
curl -XPUT 'localhost:9200/big_query/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "-1"
    }
}'
sleep 5
java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.elasticsearch.IngestESFromPostgres
sleep 5
curl -XPUT 'localhost:9200/big_query/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "60s"
    }
}'


