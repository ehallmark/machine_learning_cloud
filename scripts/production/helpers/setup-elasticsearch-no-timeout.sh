#!/usr/bin/env bash
cd /home/ehallmark1122/machine_learning_cloud/scripts/production
sudo -u ehallmark1122 bash -c '    echo "in elasticsearch"'
sudo -u ehallmark1122 bash -c '    sudo docker-compose up -d'
sudo -u ehallmark1122 bash -c '    echo "Docker compose up"'
cd /home/ehallmark1122/machine_learning_cloud