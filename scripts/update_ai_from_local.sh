#!/bin/bash

eval `ssh-agent`
ssh-add /Users/Admin/.ssh/id_rsa_ai

SERVER=ai.gttgrp.com
USER=ehallmark
while :
do

echo "Updating data..."
ssh -t $USER@$SERVER 'java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.UpdateAll'
echo "Done."

echo "Waiting a few hours before updating elasticsearch..."
sleep 10000

echo "Updating elasticsearch..."
ssh -t $USER@$SERVER 'java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.elasticsearch.IngestESFromPostgres'
echo "Done."

echo "Waiting a day to give the server some rest..."
sleep 86400

done
