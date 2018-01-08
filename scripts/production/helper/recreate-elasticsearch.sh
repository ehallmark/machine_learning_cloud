#!/usr/bin/env bash
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