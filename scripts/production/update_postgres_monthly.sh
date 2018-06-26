cd /home/ehallmark/repos/machine_learning_cloud

# ai value
psql postgres < src/seeding/google/postgres/attribute_tables/ai_value.sql

# assignee portfolio sizes
psql postgres < src/seeding/google/postgres/attribute_tables/assignee_table.sql

# run technology tagger
java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.tech_tag.PredictKeywords
psql postgres < src/seeding/google/postgres/attribute_tables/patent_keywords_aggs.sql
java -cp target/classes:"target/dependency/*" -Xms10000m -Xmx10000m seeding.google.tech_tag.FilterKeywordsByTFIDF


# run keyword prediction
