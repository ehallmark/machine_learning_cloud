#!/usr/bin/env bash
#ufw allow 22
cd /home/ehallmark1122/machine_learning_cloud
sudo -u ehallmark1122 bash -c '    sudo systemctl start mongodb'
sudo -u ehallmark1122 bash -c '    echo "Changed Dirs"'
sudo -u ehallmark1122 bash -c '    git pull origin master'
sudo -u ehallmark1122 bash -c '    echo "Pulled from git"'
sudo -u ehallmark1122 bash -c '    sudo mvn clean install'
sudo -u ehallmark1122 bash -c '    echo "Mvn clean installed"'
sudo -u ehallmark1122 bash -c '    gcsfuse machine_learning_cloud_data data'
sudo -u ehallmark1122 bash -c '    echo "Finished gcsfuse"'
cd /home/ehallmark1122/machine_learning_cloud/scripts/production
sudo -u ehallmark1122 bash -c '    echo "in elasticsearch"'
sudo -u ehallmark1122 bash -c '    sudo docker-compose up -d'
sudo -u ehallmark1122 bash -c '    echo "Docker compose up"'
cd /home/ehallmark1122/machine_learning_cloud
sleep 120s
curl -XDELETE localhost:9200/ai_db
sleep 10s
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 elasticsearch.CreatePatentDBIndex'
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "-1"
    }
}
'
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 elasticsearch.IngestMongoIntoElasticSearch'
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "60s"
    }
}
'