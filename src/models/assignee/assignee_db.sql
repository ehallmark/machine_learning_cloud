\connect assigneedb

create table if not exists assignees_raw (
    name text not null,
    city text,
    state text,
    country text not null default('US'),
    entity_status text,
    role text,
    human boolean
);

truncate assignees_raw;

select name, top[1] from (select name, array_agg(normalized_name) as top from assignees_raw where normalized_name is not null group by name,normalized_name order by name,count(*) desc) as temp limit 1000;