\connect patentdb

DROP TABLE IF EXISTS candidate_sets;
CREATE TABLE candidate_sets (
    name varchar(255) not null,
    id serial
);
