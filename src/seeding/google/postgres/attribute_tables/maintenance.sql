\connect patentdb

create table big_query_maintenance (
    publication_number varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);
