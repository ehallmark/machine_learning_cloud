\connect patentdb

drop table big_query_maintenance;
create table big_query_maintenance (
    application_number_formatted_with_country varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);

drop table big_query_maintenance_codes;
create table big_query_maintenance_codes (
    application_number_formatted_with_country varchar(32) not null,
    code varchar(32) not null,
    primary key (application_number_formatted_with_country,code)
);

create index big_query_maintenance_codes_application_number_idx on big_query_maintenance_codes (application_number_formatted_with_country);
