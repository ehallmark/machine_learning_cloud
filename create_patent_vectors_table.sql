\connect patentdb

CREATE TABLE patent_vectors (
    pub_doc_number VARCHAR(10) PRIMARY KEY,
    pub_date INTEGER,
    abstract_vectors  DOUBLE PRECISION[][],
    description_vectors DOUBLE PRECISION[][],
    claims_vectors DOUBLE PRECISION[][],
    invention_title_vectors DOUBLE PRECISION[],
    class_softmax DOUBLE PRECISION[],
    class_vectors DOUBLE PRECISION[],
    subclass_vectors DOUBLE PRECISION[]
);


CREATE INDEX patent_vectors_pub_date_idx ON patent_vectors (pub_date);

