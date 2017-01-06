\connect patentdb

DROP TABLE IF EXISTS candidate_sets;
CREATE TABLE candidate_sets (
    name varchar(255) not null,
    hidden boolean not null default(false),
    id serial
);

CREATE UNIQUE INDEX candidate_sets_name_idx ON candidate_sets (name);