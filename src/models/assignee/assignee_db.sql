\connect assigneedb

create table if not exists assignees_raw (
    name text not null,
    normalized_name text,
    city text,
    state text,
    country text,
    role text,
    human boolean
);


create table if not exists assignees (
    name text primary key,
    normalized_name text,
    city text,
    state text,
    country text,
    role text,
    human boolean
);

create index normalized_name_idx on assignees (normalized_name);

create index normalized_name_idx on assignees_raw (normalized_name);

select name, array_agg(normalized_name) as top from ( select name, normalized_name from assignees_raw where normalized_name is not null group by name order by name, count(*) desc ) sub group by name;