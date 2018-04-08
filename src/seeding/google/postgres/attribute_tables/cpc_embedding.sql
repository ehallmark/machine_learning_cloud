
\connect patentdb

create table big_query_cpc_embedding1 (
    code varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create table big_query_cpc_embedding2 (
    code varchar(32) primary key, -- eg. US9923222B1
    rnn_enc float[] not null
);
