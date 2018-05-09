\connect patentdb

create table big_query_cpc_tree (
    publication_number_full varchar(32) primary key,
    tree varchar(32)[] not null
);

insert into big_query_cpc_tree (publication_number_full,tree) (
    select publication_number_full,array_agg(distinct tr.tree) from
    patents_global as p,unnest(p.code) with ordinality as c(code,n)
    join big_query_cpc_definition as t on (c.code=t.code), unnest(t.tree) with ordinality as tr(tree,n)
    group by publication_number_full
);


create table big_query_cpc_occurrence (
    id1 integer not null,
    id2 integer not null,
    freq double precision not null
    primary key (id1,id2)
);

create table big_query_cpc_occurrence_ids (
    id integer primary key,
    code varchar(32) not null
);

create index big_query_cpc_occurrence_ids_code_idx on big_query_cpc_occurrence_ids (code);