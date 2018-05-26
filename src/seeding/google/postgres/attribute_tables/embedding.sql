
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

create table big_query_cpc_embedding1 (
    code varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create table big_query_assignee_embedding1 (
    name text primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);

create index big_query_assignee_embedding1_lower_name on big_query_assignee_embedding1 (lower(name));

-- warning patents_global_merged must already exist!
create table big_query_assignee_embedding1_help (
    name text primary key,
    code varchar(32)[] not null
);

create index patents_global_first_assignee on patents_global_merged (latest_first_assignee);
-- warning patents_global_merged must already exist!
insert into big_query_assignee_embedding1_help (name,code) (
    select latest_first_assignee,array_agg(code[floor(random()*(array_length(code,1))+1)])
    from patents_global_merged as p
    where latest_first_assignee is not null and code is not null and array_length(code,1)>0
    group by latest_first_assignee
   -- having count(*) > 2 -- maybe tune this
);
drop index patents_global_first_assignee;

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

-- warning patents_global_merged must already exist!
create table big_query_embedding_assignee_help (
    name text primary key,
    code varchar(32)[] not null
);

create index patents_global_first_assignee on patents_global_merged (latest_first_assignee);
-- warning patents_global_merged must already exist!
insert into big_query_embedding_assignee_help (name,code) (
    select latest_first_assignee,array_agg(code[floor(random()*(array_length(code,1))+1)])
    from patents_global_merged as p
    where latest_first_assignee is not null and code is not null and array_length(code,1)>0
    group by latest_first_assignee
   -- having count(*) > 2 -- maybe tune this
);
drop index patents_global_first_assignee;
