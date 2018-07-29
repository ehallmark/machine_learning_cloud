\connect patentdb

-- new embeddings (keras model)
create table big_query_embedding_by_fam (
    family_id varchar(32) primary key,
    enc float[] not null
);

create table big_query_embedding_cpc (
    code varchar(32) primary key, -- eg. US9923222B1
    enc float[] not null
);

create table big_query_embedding_assignee (
    name text primary key, -- eg. US9923222B1
    enc float[] not null
);

create table big_query_embedding_by_pub (
    publication_number_full varchar(32) primary key,
    enc float[] not null
);
