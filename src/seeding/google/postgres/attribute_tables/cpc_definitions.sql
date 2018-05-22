\connect patentdb

create table big_query_cpc_definition (
    code varchar(32) primary key,
    title_full text,
    title_part text,
    level integer,
    date_revised date,
    status text,
    parents varchar(32)[],
    children varchar(32)[],
    tree varchar(32)[]
);

-- sets the 'tree' attribute
update big_query_cpc_definition set tree=ARRAY[code]||coalesce(parents,'{}'::varchar[]);
create index big_query_cpc_definition_title_full_idx on big_query_cpc_definition (title_full);
create index big_query_cpc_definition_title_part_idx on big_query_cpc_definition (title_part);
