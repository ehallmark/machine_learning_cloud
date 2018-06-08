-- TODO make this a global thing
drop table big_query_granted;
create table big_query_granted (
    publication_number_full varchar(32) primary key,
    granted boolean not null
);

-- currently us only
insert into big_query_granted (publication_number_full,granted) (
    select p1.publication_number_full, (p1.country_code='US' and p1.publication_date <= '2000-12-31'::date) OR not bool_and(p2.kind_code like 'A%')
    from patents_global as p1
    join patents_global as p2
    on (p1.family_id=p2.family_id)
    where p1.country_code='US' and p2.country_code='US' and not p1.family_id='-1'
    group by p1.publication_number_full
);