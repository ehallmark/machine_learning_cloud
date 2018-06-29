#!/usr/bin/env bash
cd /home/ehallmark/repos/machine_learning_cloud

# backup elasticsearch datasets
mv data/elasticsearch_dataset_index.es data/elasticsearch_dataset_index.es.backup
java -cp target/classes:"target/dependency/*" -Xms5000m -Xmx5000m elasticsearch.BackupDatasetsIndexToFile

# backup
rsync -arv /home/ehallmark/repos/machine_learning_cloud/ /usb/machine_learning_cloud_backup/
rsync -arv /home/ehallmark/repos/poi/ /usb/poi_backup/
rsync -arv /home/ehallmark/data/python/ /usb/python_backup/
rsync -arv /home/ehallmark/repos/gtt_models/ /usb/gtt_models_backup/

# dump all
pg_dump -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb > /usb/big_query_database.dump

