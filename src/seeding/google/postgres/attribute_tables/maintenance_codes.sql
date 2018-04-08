\connect patentdb

create table big_query_maintenance_codes (
    publication_number varchar(32) not null,
    code varchar(32) not null,
    primary key (publication_number,code)
);

create index big_query_maintenance_codes_publication_number_idx on big_query_maintenance_codes (publication_number);
