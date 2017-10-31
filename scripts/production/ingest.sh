#!/usr/bin/env bash
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/init.sh
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.ai_db_updater.UpdateAll -2 -1 0 1 2 3 4 5 6 7 8 9 10 11'
