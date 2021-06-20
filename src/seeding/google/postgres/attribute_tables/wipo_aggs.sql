\connect patentdb


drop table big_query_wipo_by_pub;
create table big_query_wipo_by_pub (
    publication_number_full varchar(32) primary key,
    wipo_technology text[] not null
);

insert into big_query_wipo_by_pub (publication_number_full,wipo_technology) (
    select publication_number_full, array_agg(distinct replace(wipo_technology, '"', '')) from big_query_wipo as w
    inner join patents_global as p on (w.publication_number='"'||p.publication_number||'"' and p.country_code='US')
    where country_code='US'
    group by publication_number_full
);


drop table big_query_wipo_by_pub_flat;
create table big_query_wipo_by_pub_flat (
    publication_number_full varchar(32) not null,
    wipo_technology text not null,
    primary key (publication_number_full, wipo_technology)
);

insert into big_query_wipo_by_pub_flat (publication_number_full,wipo_technology) (
    select distinct on (publication_number_full, wipo_technology)
        publication_number_full, replace(wipo_technology, '"', '') from big_query_wipo as w
    inner join patents_global as p on (w.publication_number='"'||p.publication_number||'"' and p.country_code='US')
    where country_code='US'
    order by publication_number_full, wipo_technology, random()
);
