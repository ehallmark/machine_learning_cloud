#!/usr/bin/env bash
# turn off elasticsearch
cd /home/ehallmark/repos/machine_learning_cloud/scripts/production
sudo docker-compose down
cd /home/ehallmark/repos/machine_learning_cloud

# run part 1 of updates
java -cp target/classes:"target/dependency/*" -Xms90000m -Xmx90000m seeding.IngestRecentUpdatesPart1
java -cp target/classes:"target/dependency/*" -Xms90000m -Xmx90000m models.classification_models.UpdateClassificationModels


# turn on elastic search
cd /home/ehallmark/repos/machine_learning_cloud/scripts/production
sudo docker-compose up -d
cd /home/ehallmark/repos/machine_learning_cloud
# wait for elastic search
echo "Waiting for Elasticsearch to start..."
until $(curl --output /dev/null --silent --head --fail http://127.0.0.1:9200); do
    printf '.'
    sleep 5
done
echo "Elasticsearch started."
sleep 10s

# then run part 2 of updates
java -cp target/classes:"target/dependency/*" -Xms60000m -Xmx60000m seeding.IngestRecentUpdatesPart2

# then turn off elastic search
cd /home/ehallmark/repos/machine_learning_cloud/scripts/production
sudo docker-compose down
cd /home/ehallmark/repos/machine_learning_cloud

# ingest computable
java -cp target/classes:"target/dependency/*" -Xms90000m -Xmx90000m seeding.ai_db_updater.UpdateAll 14

# update elastic search
cd /home/ehallmark/repos/machine_learning_cloud/scripts/production
sudo docker-compose up -d
cd /home/ehallmark/repos/machine_learning_cloud
# wait for elastic search to start
echo "Waiting for Elasticsearch to start..."
until $(curl --output /dev/null --silent --head --fail http://127.0.0.1:9200); do
    printf '.'
    sleep 5
done
echo "Elasticsearch started."
sleep 20s
curl -XDELETE localhost:9200/ai_db
sleep 10s
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m elasticsearch.CreatePatentDBIndex
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "-1"
    }
}
'
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m elasticsearch.IngestMongoIntoElasticSearch
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "125s"
    }
}
'

# backup elasticsearch datasets
mv data/elasticsearch_dataset_index.es data/elasticsearch_dataset_index.es.backup
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m elasticsearch.BackupDatasetsIndexToFile



