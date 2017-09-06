-- CONNECT TO PROPER DATABASE!
\connect patentdb

-- CREATE THE TABLE FOR PATENT TEXT
CREATE TABLE patents_and_applications (
    pub_doc_number varchar(25) primary key,
    doc_type varchar(25) not null,
    tokens tsvector not null
);

-- TEXT INDEX
CREATE INDEX patents_and_applications_tokens_idx on patents_and_applications USING GIN (tokens);
CREATE INDEX patents_and_applications_doc_type_idx on patents_and_applications (doc_type);
CREATE INDEX patents_and_applications_tokens_patents_idx on patents_and_applications USING GIN (tokens) where doc_type='patents';
CREATE INDEX patents_and_applications_tokens_applications_idx on patents_and_applications USING GIN (tokens) where doc_type='applications';

pg_dump -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb -t patents_and_applications > data/patents_and_applications.dump

-- To restore
pg_restore -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb -t patents_and_applications data/patents_and_applications.dump

-- COMPDB AND GATHER
-- Make sure to create the database first!!
-- pg_restore -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/compdb_production data/compdb_production.dump
-- pg_restore -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/gather_production data/gather_production.dump