#!/usr/bin/env bash
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/init.sh
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/setup-elasticsearch.sh
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "-1"
    }
}
'
sleep 10s
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms25000m -Xmx25000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.ai_db_updater.UpdateCompDBAndGatherData'
curl -XPUT 'localhost:9200/ai_db/_settings?pretty' -H 'Content-Type: application/json' -d'
{
    "index" : {
        "refresh_interval" : "60s"
    }
}
'
