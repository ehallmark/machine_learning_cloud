\connect patentdb

CREATE TABLE raw_patents (
    name VARCHAR(50) PRIMARY KEY,
    raw_text TEXT
);

alter table raw_patents add column vector double precision[];

alter table raw_patents add column words text[];
update raw_patents set words=array_remove(regexp_split_to_array(raw_text, '[\s+]'),'');

