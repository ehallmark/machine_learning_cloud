\connect patentdb

create table big_query_technologies (
    family_id varchar(32) primary key, -- eg. US9923222B1
    technology text not null,
    secondary text not null
);


create table big_query_technologies2 (
    family_id varchar(32) primary key, -- eg. US9923222B1
    publication_number_full varchar(32) not null,
    technology text not null,
    technology2 text not null,
    technology3 text[]
);
