\connect patentdb

CREATE TABLE candidate_sets (
    name varchar(255) primary key,
    doc_numbers varchar(20)[]
);
