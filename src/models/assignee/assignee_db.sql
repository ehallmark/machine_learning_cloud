\connect assigneedb

create table if not exists assignees (
    name text primary key,
    normalized_name text,
    city text,
    state text,
    country text,
    role text,
    human boolean,
    execution_date date
);

create index normalized_name_idx on assignees (normalized_name);