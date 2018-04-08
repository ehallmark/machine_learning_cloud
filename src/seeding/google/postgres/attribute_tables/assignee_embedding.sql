
\connect patentdb

create table big_query_assignee_embedding1 (
    name varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create table big_query_assignee_embedding2 (
    name varchar(32) primary key, -- eg. US9923222B1
    rnn_enc float[] not null
);