cd /home/ehallmark/repos/machine_learning_cloud

java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.IngestMaintenanceFeeData
psql postgres < src/seeding/google/postgres/attribute_tables/maintenance_codes_aggs.sql

java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.IngestAssignmentData
psql postgres < src/seeding/google/postgres/attribute_tables/assignment_aggs.sql
psql postgres < src/seeding/google/postgres/attribute_tables/latest_assignee.sql

java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.IngestWIPOTechnologies
psql postgres < src/seeding/google/postgres/attribute_tables/wipo_aggs.sql

java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.DownloadLatestPAIR
java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.IngestPairData
psql postgres < src/seeding/google/postgres/attribute_tables/pair_aggs.sql

java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.postgres.IngestPTABData
psql postgres < src/seeding/google/postgres/attribute_tables/ptab_aggs.sql


# update
source scripts/production/backup.sh


