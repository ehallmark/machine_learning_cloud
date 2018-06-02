\connect patentdb

create table big_query_maintenance (
    publication_number varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);

create table big_query_maintenance_codes (
    publication_number varchar(32) not null,
    code varchar(32) not null,
    primary key (publication_number,code)
);

create index big_query_maintenance_codes_publication_number_idx on big_query_maintenance_codes (publication_number);

drop table big_query_maintenance_codes_by_app;
create table big_query_maintenance_codes_by_app (
    application_number_full varchar(32) primary key,
    codes varchar(32)[] not null
);

insert into big_query_maintenance_codes_by_app (application_number_full,codes) (
    select p.application_number_full, array_agg(distinct m.code)
    from big_query_maintenance_codes as m
    inner join patents_global as p on (p.publication_number=m.publication_number AND p.country_code='US')
    where p.country_code='US'
    group by application_number_full
);

drop table big_query_maintenance_codes_by_pub;
create table big_query_maintenance_codes_by_pub (
    publication_number_full varchar(32) primary key,
    codes varchar(32)[] not null
);

insert into big_query_maintenance_codes_by_pub (publication_number_full,codes) (
    select p.publication_number_full, array_agg(m.code)
    from patents_global as p
    join big_query_maintenance_codes as m
    on (p.publication_number=m.publication_number AND p.country_code='US')
    group by p.publication_number_full
);

-- get any missing apps
insert into big_query_maintenance_codes_by_pub (publication_number_full,codes) (
    select distinct p.publication_number_full, ma.codes
    from patents_global as p
    join big_query_maintenance_codes_by_app as ma on (p.application_number_full=ma.application_number_full)
    full outer join big_query_maintenance_codes_by_pub as already_exist
    on (already_exist.publication_number_full=p.publication_number_full)
    where already_exist.publication_number_full is null
);

drop table big_query_maintenance_by_app;
create table big_query_maintenance_by_app (
    application_number_full varchar(32) primary key,
    country_code varchar(8) not null,
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);

insert into big_query_maintenance_by_app (application_number_full,country_code,original_entity_status,lapsed,reinstated) (
    select distinct on (p.application_number_full) p.application_number_full,'US',m.original_entity_status,m.lapsed,m.reinstated from big_query_maintenance as m
    inner join patents_global as p on (p.publication_number=m.publication_number AND p.country_code='US')
    where p.country_code='US'
    order by p.application_number_full,publication_date desc nulls last
);


drop table big_query_maintenance_by_pub;
create table big_query_maintenance_by_pub (
    publication_number_full varchar(32) primary key,
    country_code varchar(8) not null,
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);

insert into big_query_maintenance_by_pub (publication_number_full,country_code,original_entity_status,lapsed,reinstated) (
    select distinct on (p.publication_number_full) p.publication_number_full,'US',m.original_entity_status,m.lapsed,m.reinstated from big_query_maintenance as m
    inner join patents_global as p on (p.publication_number=m.publication_number AND p.country_code='US')
    where p.country_code='US'
    order by p.publication_number_full,publication_date desc nulls last
);
-- get any missing apps
insert into big_query_maintenance_by_pub (
    select distinct p.publication_number_full, ma.country_code, ma.original_entity_status, ma.lapsed, ma.reinstated
    from patents_global as p
    join big_query_maintenance_by_app as ma on (p.application_number_full=ma.application_number_full)
    full outer join big_query_maintenance_by_pub as already_exist
    on (already_exist.publication_number_full=p.publication_number_full)
    where already_exist.publication_number_full is null
);