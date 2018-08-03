#!/usr/bin/env bash

SLEEP_TIME=1d


# start python similarity vectorizer
source /home/ehallmark/environments/tfenv/bin/activate


while :
do
    # start python encoding server for text vectorizer
    cd /home/ehallmark/repos/gtt_models/
    python3 src/server/EncodingServer.py &
    ENCODER_PID=$!
    echo "$ENCODER_PID" > /home/ehallmark/repos/machine_learning_cloud/python_server.pid

    # start server and save PID to /home/ehallmark/repos/machine_learning_cloud/app.pid
    cd /home/ehallmark/repos/machine_learning_cloud
    java -cp target/classes:"target/dependency/*" -Xms15000m -Xmx15000m user_interface.server.BigQueryServer &
    APP_PID=$!
    echo "$APP_PID" > /home/ehallmark/repos/machine_learning_cloud/app.pid

    sleep 1h
    # app is now running!


    sleep $SLEEP_TIME
    while ps -p $APP_PID
    do
        echo "Trying to kill java server..."
        kill $APP_PID
        sleep 5s
    done

    echo "Starting updates..."
    source scripts/production/update_postgres_weekly.sh

    # backup
    echo "Backing up..."
    source scripts/production/backup.sh

    echo "Indexing updates..."
    source scripts/production/update_elasticsearch.sh


    # turn off python server for no real reason
    while ps -p $ENCODER_PID
    do
        echo "Trying to kill python server..."
        kill $ENCODER_PID
        sleep 5s
    done

done