\connect patentdb

create table big_query_cpc_tree_by_fam (
    family_id varchar(32) primary key,
    tree varchar(32)[] not null
);

insert into big_query_cpc_tree_by_fam (family_id,tree) (
    select family_id,array_agg(distinct tr.tree) from
    patents_global as p,unnest(p.code) with ordinality as c(code,n)
    join big_query_cpc_definition as t on (c.code=t.code), unnest(t.tree) with ordinality as tr(tree,n)
    where family_id != '-1'
    group by family_id
);


drop table big_query_cpc_tree;
create table big_query_cpc_tree (
    publication_number_full varchar(32) primary key,
    family_id varchar(32),
    tree varchar(32)[] not null
);


insert into big_query_cpc_tree (publication_number_full,family_id,tree) (
    select publication_number_full,family_id,array_agg(distinct tr.tree) from
    patents_global as p,unnest(p.code) with ordinality as c(code,n)
    join big_query_cpc_definition as t on (c.code=t.code), unnest(t.tree) with ordinality as tr(tree,n)
    group by publication_number_full
);

create index big_query_cpc_tree_family_id_idx on big_query_cpc_tree (family_id);

create table big_query_cpc_occurrence (
    id1 integer,
    id2 integer,
    freq double precision
);

create table big_query_cpc_occurrence_ids (
    id integer primary key,
    code varchar(32) not null
);

create index big_query_cpc_occurrence_ids_code_idx on big_query_cpc_occurrence_ids (code);

