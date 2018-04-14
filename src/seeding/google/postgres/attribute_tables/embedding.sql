
\connect patentdb

create table big_query_embedding1_help (
    cpc_str text primary key,
    cpc_vae float[] not null
);

create table big_query_embedding1_help_by_pub (
    publication_number_full varchar(32) primary key,
    cpc_str text not null
);

create index big_query_embedding1_help_by_pub_idx on big_query_embedding1_help_by_pub (cpc_str);

create table big_query_embedding1 (
    family_id varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create table big_query_embedding2 (
    family_id varchar(32) primary key, -- eg. US9923222B1
    rnn_enc float[] not null
);


-- build embedding 1
insert into big_query_embedding1 (family_id,cpc_vae) (
    select distinct on (p.family_id) p.family_id,cpc_vae
    from big_query_embedding1_help_by_pub as e
    join big_query_embedding1_help as h on (e.cpc_str=h.cpc_str)
    join patents_global as p on (e.publication_number_full=p.publication_number_full)
    where p.family_id != '-1'
    order by p.family_id, p.publication_date desc nulls last
);

-- build embedding 2