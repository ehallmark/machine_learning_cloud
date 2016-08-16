\connect patentdb

DROP TABLE IF EXISTS last_vectors_ingest;

CREATE TABLE last_vectors_ingest (
    program_name VARCHAR(255) PRIMARY KEY,
    pub_date INTEGER
);

INSERT INTO last_vectors_ingest (program_name,pub_date) VALUES ('patent_vectors',20000000), ('classification_vectors', 20000000), ('claim_vectors', 20000000);

