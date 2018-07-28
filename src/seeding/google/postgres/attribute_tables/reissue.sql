\connect patentdb

drop table big_query_reissue;
create table big_query_reissue (
    publication_number_full varchar(32) primary key,
    original_kind_code varchar(8) not null
);

insert into big_query_reissue (
    select p.publication_number_full,
    mode() within group (order by p2.kind_code)
    from patents_global as p
    join patents_global as p2
    on ((p.application_number_formatted,p.country_code)=(p2.application_number_formatted,p2.country_code))
    where p.country_code = 'US' and p.kind_code like 'E%'
    and not p2.kind_code like 'E%'
    group by p.publication_number_full
);