#!/usr/bin/env bash
cd /home/ehallmark/machine_learning_cloud/scripts/production
sudo -u ehallmark bash -c '    echo "in elasticsearch"'
sudo -u ehallmark bash -c '    sudo docker-compose up -d'
sudo -u ehallmark bash -c '    echo "Docker compose up"'
cd /home/ehallmark/machine_learning_cloud