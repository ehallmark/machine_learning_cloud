#!/usr/bin/env bash
cd /home/ehallmark/repos/machine_learning_cloud

# backup mongodb
mv /usb/data/ai_db.gz /usb/data/ai_db.backup.gz
mongodump --archive=/usb/data/ai_db.gz --gzip --db ai_db

# backup
rsync -arv /home/ehallmark/repos/machine_learning_cloud/data/ /usb/data/


