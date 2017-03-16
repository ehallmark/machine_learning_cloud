gcloud auth application-default login
sudo mkdir /mnt/bucket
sudo chmod a+w /mnt/bucket
gcsfuse image-scrape-dump /mnt/bucket