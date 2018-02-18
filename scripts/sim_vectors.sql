\connect patentdb

create table sim_vectors (
    filing text primary key,
    vector double precision[] not null
);