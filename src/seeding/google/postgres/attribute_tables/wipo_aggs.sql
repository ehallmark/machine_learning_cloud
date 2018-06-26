\connect patentdb

drop table big_query_wipo_by_family;
create table big_query_wipo_by_family (
    family_id varchar(32) primary key,
    wipo_technology text[] not null
);

insert into big_query_wipo_by_family (family_id,wipo_technology) (
    select family_id, array_agg(distinct wipo_technology) from big_query_wipo as w
    inner join patents_global as p on (w.publication_number=p.publication_number and p.country_code='US')
    where family_id != '-1' and country_code='US'
    group by family_id
);
