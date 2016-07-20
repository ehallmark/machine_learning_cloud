\connect patentdb

DROP TABLE IF EXISTS patent_vectors;

CREATE TABLE patent_vectors (
    pub_doc_number INTEGER PRIMARY KEY,
    pub_date INTEGER,
    is_valuable BOOLEAN NOT NULL DEFAULT(FALSE),
    is_testing BOOLEAN NOT NULL DEFAULT(FALSE),
    abstract_vectors  DOUBLE PRECISION[][],
    description_vectors DOUBLE PRECISION[][],
    claims_vectors DOUBLE PRECISION[][],
    invention_title_vectors DOUBLE PRECISION[][],
    class_softmax DOUBLE PRECISION[][],
    class_vectors DOUBLE PRECISION[][],
    subclass_vectors DOUBLE PRECISION[][]
);


CREATE INDEX patent_vectors_pub_date_idx ON patent_vectors (pub_date);