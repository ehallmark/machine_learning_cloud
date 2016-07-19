\connect patentdb

CREATE TABLE patent_vectors (
    pub_doc_number INTEGER PRIMARY KEY,
    pub_date INTEGER,
    is_valuable BOOLEAN NOT NULL DEFAULT(FALSE),
    is_testing BOOLEAN NOT NULL DEFAULT(FALSE),
    abstract_pv  DOUBLE PRECISION[],
    description_pv DOUBLE PRECISION[],
    claims_pv DOUBLE PRECISION[],
    invention_title_wv DOUBLE PRECISION[],
    classes_iv DOUBLE PRECISION[],
    classes_wv DOUBLE PRECISION[],
    subclasses_wv DOUBLE PRECISION[]
);


CREATE INDEX patent_vectors_pub_date_idx ON patent_vectors (pub_date);