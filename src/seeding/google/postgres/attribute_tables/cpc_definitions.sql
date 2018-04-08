\connect patentdb

create table big_query_cpc_definition (
    code varchar(32) primary key,
    title_full text,
    title_part text,
    level integer,
    date_revised date,
    status text,
    parents varchar(32)[],
    children varchar(32)[]
);

create table big_query_cpc_definition_tree (
    code varchar(32) primary key,
    tree varchar(32)[] not null
);

