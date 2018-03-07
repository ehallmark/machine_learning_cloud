#!/usr/bin/env bash
cd /home/ehallmark/machine_learning_cloud
sudo -u mongodb mongod -f /etc/mongod.conf --fork
sudo -u ehallmark bash -c '    echo "Changed Dirs"'
sudo -u ehallmark bash -c '    git pull origin master'
sudo -u ehallmark bash -c '    echo "Pulled from git"'
sudo -u ehallmark bash -c '    sudo mvn clean install'
sudo -u ehallmark bash -c '    echo "Mvn clean installed"'
sudo -u ehallmark bash -c '    gcsfuse machine_learning_cloud_data data'
sudo -u ehallmark bash -c '    echo "Finished gcsfuse"'