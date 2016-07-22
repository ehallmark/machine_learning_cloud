\connect patentdb

DROP TABLE IF EXISTS last_vectors_ingest;

CREATE TABLE last_vectors_ingest (
    program_name VARCHAR(255),
    pub_date INTEGER
);

INSERT INTO last_vectors_ingest (program_name,pub_date) VALUES ('patent_vectors',20050000), ('classification_vectors', 20050000), ('claim_vectors', 20050000);

