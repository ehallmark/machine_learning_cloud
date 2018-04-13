\connect patentdb

create table big_query_maintenance_codes (
    publication_number varchar(32) not null,
    code varchar(32) not null,
    primary key (publication_number,code)
);

create index big_query_maintenance_codes_publication_number_idx on big_query_maintenance_codes (publication_number);

create table big_query_maintenance_codes_by_pub (
    publication_number_full varchar(32) primary key,
    codes varchar(32)[] not null
);

insert into big_query_maintenance_codes_by_pub (publication_number_full,codes) (
    select p.publication_number_full,codes from (
        select publication_number,array_agg(code) as codes from big_query_maintenance_codes group by publication_number
    ) as m
    inner join patents_global as p on (p.publication_number=m.publication_number AND p.country_code='US')
    where p.country_code='US' and family_id != '-1'
);
