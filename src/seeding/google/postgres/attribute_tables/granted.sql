-- TODO make this a global thing
drop table big_query_granted;
create table big_query_granted (
    publication_number_full varchar(32) primary key,
    granted boolean not null
);

-- currently us only
insert into big_query_granted (publication_number_full,granted) (
    select publication_number_full, (country_code='US' and publication_date <= '2000-12-31'::date) OR not kind_code like 'A%'
    from patents_global
    where country_code='US' OR country_code='EP'
);

