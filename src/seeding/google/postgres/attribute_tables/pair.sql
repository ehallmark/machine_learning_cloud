\connect patentdb

create table big_query_pair (
    application_number_formatted varchar(32) primary key,
    publication_number varchar(32),
    original_entity_type varchar(32),
    status text,
    status_date date,
    abandoned boolean, -- from status, whether app was abandoned
    term_adjustments integer
);

drop table big_query_pair_by_pub;
create table big_query_pair_by_pub (
    publication_number_full varchar(32) primary key,
    original_entity_type varchar(32),
    status text,
    status_date date,
    abandoned boolean, -- from status, whether app was abandoned
    term_adjustments integer
);

insert into big_query_pair_by_pub (publication_number_full,original_entity_type,status,status_date,abandoned,term_adjustments) (
    select distinct on (p.publication_number_full)
     p.publication_number_full, pair.original_entity_type, status, status_date, abandoned, term_adjustments
    from big_query_pair as pair
    inner join patents_global as p on (p.application_number_formatted=pair.application_number_formatted AND p.country_code='US')
    where p.country_code='US' and p.application_number_formatted is not null and family_id!='-1'
    order by p.publication_number_full,p.publication_date desc nulls last
);

