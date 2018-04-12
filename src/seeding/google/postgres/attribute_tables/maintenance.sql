\connect patentdb

create table big_query_maintenance (
    publication_number varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);


create table big_query_maintenance_family_id (
    publication_number varchar(32) primary key,
    family_id not null
);



create index big_query_maintenance_family_id_idx on big_query_maintenance_family_id (publication_number,family_id);