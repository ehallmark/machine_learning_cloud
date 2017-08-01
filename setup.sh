#!/usr/bin/env bash
cd /home/ehallmark1122/machine_learning_cloud
sudo -u ehallmark1122 bash -c '    echo "Changed Dirs"'
sudo -u ehallmark1122 bash -c '    git pull origin master'
sudo -u ehallmark1122 bash -c '    echo "Pulled from git"'
mvn clean install
sudo -u ehallmark1122 bash -c '    echo "Mvn clean installed"'
sudo -u ehallmark1122 bash -c '    gcsfuse machine_learning_cloud_data data'
sudo -u ehallmark1122 bash -c '    echo "Finished gcsfuse"'
cd /home/ehallmark1122/machine_learning_cloud/src/elasticsearch
sudo -u ehallmark1122 bash -c '    echo "in elasticsearch"'
sudo -u ehallmark1122 bash -c '    docker-compose up -d'
sudo -u ehallmark1122 bash -c '    echo "Docker compose up"'
cd /home/ehallmark1122/machine_learning_cloud
sudo -u ehallmark1122 bash -c '    echo "Back to src dir" '
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms20000m -Xmx20000m user_interface.server.SimilarPatentServer &'
