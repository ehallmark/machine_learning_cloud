#!/usr/bin/env bash
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/init-no-mongo.sh
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/setup-elasticsearch-no-timeout.sh
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms80000m -Xmx80000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 user_interface.server.SimilarPatentServer &'
