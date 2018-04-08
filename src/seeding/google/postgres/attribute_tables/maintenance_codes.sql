\connect patentdb

create table big_query_maintenance_codes (
    publication_number varchar(32) not null references big_query_maintenance (publication_number), -- eg. 8923222
    code varchar(32) not null,
    primary key (publication_number,code)
);
