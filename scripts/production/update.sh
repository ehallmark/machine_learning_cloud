#!/usr/bin/env bash

# run part 1 of updates
java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.IngestRecentUpdatesPart1

# turn on elastic search
cd /home/ehallmark/machine_learning_cloud/scripts/production
sudo docker-compose up -d
cd /home/ehallmark/machine_learning_cloud
sleep 200s

# then run part 2 of updates
java -cp target/classes:"target/dependency/*" -Xms40000m -Xmx40000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.IngestRecentUpdatesPart2

# then turn off elastic search
cd /home/ehallmark/machine_learning_cloud/scripts/production
sudo docker-compose down
cd /home/ehallmark/machine_learning_cloud

# ingest computable
java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.ai_db_updater.UpdateAll 12

# update elastic search
cd /home/ehallmark/machine_learning_cloud/scripts/production
sudo docker-compose up -d
cd /home/ehallmark/machine_learning_cloud
sleep 200s

curl -XDELETE localhost:9200/ai_db
sleep 10s
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 elasticsearch.CreatePatentDBIndex
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "-1"
    }
}
'
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 elasticsearch.IngestMongoIntoElasticSearch
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "15s"
    }
}
'

# backup elasticsearch datasets
mv data/elasticsearch_dataset_index.es data/elasticsearch_dataset_index.es.backup
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 elasticsearch.BackupDatasetsIndexToFile

# then turn off elastic search
cd /home/ehallmark/machine_learning_cloud/scripts/production
sudo docker-compose down
cd /home/ehallmark/machine_learning_cloud

# backup mongodb
mv /usb/data/ai_db.gz /usb/data/ai_db.backup.gz
mongodump --archive=/usb/data/ai_db.gz --gzip --db ai_db

# backup
rsync -arv /home/ehallmark/repos/machine_learning_cloud/data/ /usb/data/

