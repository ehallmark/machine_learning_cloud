\connect patentdb

-- COMPDB initial table
create table big_query_compdb_deals (
    deal_id varchar(32) primary key,
    recorded_date date,
    technology text[],
    inactive boolean,
    acquisition boolean,
    reel_frame varchar(50)[] not null
    --buyer text[],
    --seller text[]
);

-- GATHER initial table
create table big_query_gather (
    publication_number varchar(32) primary key,
    value integer,
    stage varchar(32)[],
    technology text[]
);
