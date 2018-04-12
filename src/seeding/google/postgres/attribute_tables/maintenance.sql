\connect patentdb

create table big_query_maintenance (
    publication_number varchar(32) primary key, -- eg. 8923222
    original_entity_status varchar(32),
    lapsed boolean not null,
    reinstated boolean not null
);


create table big_query_maintenance_family_id (
    publication_number varchar(32) primary key,
    family_id varchar(32) not null
);

insert into big_query_maintenance_family_id (publication_number,family_id) (
    select distinct on (p.publication_number) p.publication_number,p.family_id from big_query_maintenance as m
    inner join patents_global as p on (p.publication_number=m.publication_number AND p.country_code='US')
    where p.country_code='US'
    order by p.publication_number,publication_date desc nulls last
);

create index big_query_maintenance_family_id_idx on big_query_maintenance_family_id (publication_number,family_id);

