#!/usr/bin/env bash
cd /home/ehallmark1122/machine_learning_cloud
echo "Changed Dirs"
git pull origin master
echo "Pulled from git"
mvn clean install
echo "Mvn clean installed"
gcsfuse machine_learning_cloud_data data
echo "Finished gcsfuse"
cd /home/ehallmark1122/machine_learning_cloud/src/elasticsearch
echo "in elasticsearch"
#docker-compose up -d
echo "Docer compose up"
cd /home/ehallmark1122/machine_learning_cloud
echo "Back to src dir"
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m user_interface.server.SimilarPatentServer &
echo "Starting Java server"

