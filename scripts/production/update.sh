#!/usr/bin/env bash
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/init.sh

# run part 1 of updates
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms100000m -Xmx100000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.IngestRecentUpdatesPart1'

# turn on elastic search
source /home/ehallmark1122/machine_learning_cloud/scripts/production/helper/setup-elasticsearch.sh

# then run part 2 of updates
sudo -u ehallmark1122 bash -c '    java -cp target/classes:"target/dependency/*" -Xms30000m -Xmx30000m -Dcom.sun.management.jmxremote.port=4567 -Dcom.sun.management.jmxremote.rmi.port=4567 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 seeding.IngestRecentUpdatesPart2'

// backup

rsync -arv /home/ehallmark/repos/machine_learning_cloud/data/ /usb/data/