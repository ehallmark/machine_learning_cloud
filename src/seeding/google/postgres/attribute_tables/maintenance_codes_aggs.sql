\connect patentdb


drop table big_query_maintenance_codes_by_pub;
create table big_query_maintenance_codes_by_pub (
    publication_number_full varchar(32) primary key,
    codes varchar(32)[] not null
);

insert into big_query_maintenance_codes_by_pub (publication_number_full,codes) (
    select publication_number_full, array_agg(m.code)
    from big_query_maintenance_codes as m
    inner join patents_global as p on ('US'||p.application_number_formatted=m.application_number_formatted_with_country)
    where p.country_code='US' and p.application_number_formatted is not null
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
    inner join patents_global as p on ('US'||p.application_number_formatted=m.application_number_formatted_with_country)
    where p.country_code='US' and p.application_number_formatted is not null
    order by p.publication_number_full,publication_date desc nulls last
);