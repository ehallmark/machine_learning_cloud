#!/usr/bin/env bash
git pull origin master
mvn clean install
gcsfuse machine_learning_cloud_data data
java -cp target/classes:"target/dependency/*" project_box.PGDumpLatest
