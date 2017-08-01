#!/usr/bin/env bash
sudo su - ehallmark1122
cd /home/ehallmark1122/machine_learning_cloud
git pull origin master
mvn clean install
gcsfuse machine_learning_cloud_data data
cd src/elasticsearch
#docker-compose up -d
cd ../../
java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m user_interface.server.SimilarPatentServer &


