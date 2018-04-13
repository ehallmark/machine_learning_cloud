\connect patentdb

create table big_query_maintenance (
    publication_number varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);


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
    where p.country_code='US' and family_id != '-1'
    order by p.publication_number_full,publication_date desc nulls last
);
