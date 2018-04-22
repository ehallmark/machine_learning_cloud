
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
    name varchar(32) primary key, -- eg. US9923222B1
    cpc_vae float[] not null
);


-- creates assignee vectors by averaging from patent vectors
insert into big_query_assignee_embedding1 (name,cpc_vae) (
    select first_assignee,
        Array[
            sum(cpc_vae[1])/count(*),
            sum(cpc_vae[2])/count(*),
            sum(cpc_vae[3])/count(*),
            sum(cpc_vae[4])/count(*),
            sum(cpc_vae[5])/count(*),
            sum(cpc_vae[6])/count(*),
            sum(cpc_vae[7])/count(*),
            sum(cpc_vae[8])/count(*),
            sum(cpc_vae[9])/count(*),
            sum(cpc_vae[10])/count(*),
            sum(cpc_vae[11])/count(*),
            sum(cpc_vae[12])/count(*),
            sum(cpc_vae[13])/count(*),
            sum(cpc_vae[14])/count(*),
            sum(cpc_vae[15])/count(*),
            sum(cpc_vae[16])/count(*),
            sum(cpc_vae[17])/count(*),
            sum(cpc_vae[18])/count(*),
            sum(cpc_vae[19])/count(*),
            sum(cpc_vae[20])/count(*),
            sum(cpc_vae[21])/count(*),
            sum(cpc_vae[22])/count(*),
            sum(cpc_vae[23])/count(*),
            sum(cpc_vae[24])/count(*),
            sum(cpc_vae[25])/count(*),
            sum(cpc_vae[26])/count(*),
            sum(cpc_vae[27])/count(*),
            sum(cpc_vae[28])/count(*),
            sum(cpc_vae[29])/count(*),
            sum(cpc_vae[30])/count(*),
            sum(cpc_vae[31])/count(*),
            sum(cpc_vae[32])/count(*)
        ] as vector
    from big_query_embedding1 as e
    join big_query_patent_to_latest_assignee_by_family as f
    on (e.family_id=f.family_id)
    group by first_assignee
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