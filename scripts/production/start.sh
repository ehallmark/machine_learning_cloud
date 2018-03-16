#!/usr/bin/env bash
cd /home/ehallmark/repos/machine_learning_cloud

# start server and save PID to /home/ehallmark/repos/machine_learning_cloud/app.pid

java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m user_interface.server.SimilarPatentServer &
echo "$!" > /home/ehallmark/repos/machine_learning_cloud/app.pid