
\connect patentdb

create table big_query_embedding1_help (
    cpc_str text primary key,
    cpc_vae float[] not null
);



create table big_query_embedding1 (
    family_id varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create table big_query_embedding2 (
    family_id varchar(32) primary key, -- eg. US9923222B1
    rnn_enc float[] not null
);
