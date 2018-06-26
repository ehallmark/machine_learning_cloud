#!/usr/bin/env bash
cd /home/ehallmark/repos/machine_learning_cloud

# start python similarity vectorizer
source /home/ehallmark/environments/tfenv/bin/activate
cd /home/ehallmark/repos/gtt_models/
python3 src/java_compatibility/BuildCPCEncodings.py &
echo "$!" > /home/ehallmark/repos/machine_learning_cloud/python_server.pid
cd /home/ehallmark/repos/machine_learning_cloud

# start server and save PID to /home/ehallmark/repos/machine_learning_cloud/app.pid
java -cp target/classes:"target/dependency/*" -Xms15000m -Xmx15000m user_interface.server.BigQueryServer &
echo "$!" > /home/ehallmark/repos/machine_learning_cloud/app.pid
