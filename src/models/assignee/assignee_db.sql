\connect assigneedb

create table if not exists assignees_raw (
    name text not null,
    normalized_name text,
    city text,
    state text,
    country text not null default('US'),
    entity_status text,
    role text,
    human boolean
);

truncate assignees_raw;

create index assignees_raw_normalized_name_idx on assignees_raw (normalized_name);

select name, top[1] from (select name, array_agg(normalized_name) as top from assignees_raw where normalized_name is not null group by name,normalized_name order by name,count(*) desc) as temp limit 1000;