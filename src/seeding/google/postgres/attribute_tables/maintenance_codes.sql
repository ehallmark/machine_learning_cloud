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


drop table big_query_maintenance_codes_by_pub;
create table big_query_maintenance_codes_by_pub (
    publication_number_full varchar(32) primary key,
    codes varchar(32)[] not null
);

insert into big_query_maintenance_codes_by_pub (application_number_full,codes) (
    select publication_number_full, array_agg(m.code)
    from big_query_maintenance_codes as m
    inner join patents_global as p on (p.application_number_formatted_with_country=m.application_number_formatted_with_country)
    where p.country_code='US'
    group by publication_number_full
);


drop table big_query_maintenance_by_pub;
create table big_query_maintenance_by_pub (
    publication_number_full varchar(32) primary key,
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);

insert into big_query_maintenance_by_pub (publication_number_full,original_entity_status,lapsed,reinstated) (
    select distinct on (p.publication_number_full) p.publication_number_full,m.original_entity_status,m.lapsed,m.reinstated from big_query_maintenance as m
    inner join patents_global as p on (p.application_number_formatted_with_country=m.application_number_formatted_with_country)
    where p.country_code='US'
    order by p.publication_number_full,publication_date desc nulls last
);