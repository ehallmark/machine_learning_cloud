#!/usr/bin/env bash
source /home/ehallmark1122/machine_learning_cloud/scripts/production/ingest.sh

# rerun page rank
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 models.value_models.graphical.UpdateGraphicalModels'

# setup elasticsearch
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/setup-elasticsearch.sh

# build elasticsearch
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/recreate-elasticsearch.sh

# run models
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 models.UpdateModels'

# turn off elasticsearch
cd /home/ehallmark1122/machine_learning_cloud/scripts/production
sudo -u ehallmark1122 bash -c '    sudo docker-compose down'
sleep 10s
cd /home/ehallmark1122/machine_learning_cloud

# run similarity encoding (with more memory)
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 models.similarity_models.UpdateSimilarityModels'

# reingest computable attributes
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.ai_db_updater.UpdateAll 11 '

# turn on elasticsearch
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/setup-elasticsearch.sh

# build elasticsearch
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/recreate-elasticsearch.sh
