\connect patentdb

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
     p.publication_number_full, pair.original_entity_type, status, status_date, abandoned, coalesce(pair.term_adjustments,pta.term_adjustments)
    from patents_global as p
    left join big_query_pair as pair on (p.application_number_formatted=pair.application_number_formatted AND p.country_code='US')
    left join big_query_pta as pta on (p.application_number_formatted=pta.application_number_formatted AND p.country_code='US')
    where p.country_code='US' and p.application_number_formatted is not null
    order by p.publication_number_full,p.publication_date desc nulls last
);


-- TODO make this a global thing
drop table big_query_priority_and_expiration;
create table big_query_priority_and_expiration (
    publication_number_full varchar(32) primary key,
    priority_date date not null,
    priority_date_est date not null,
    expiration_date_est date,
    term_adjustments integer
);


insert into big_query_priority_and_expiration (
    select p.publication_number_full,
    coalesce(p.priority_date, p.filing_date),
    coalesce(p.priority_date, p.filing_date) + interval '1' day * coalesce(pair.term_adjustments,0),
    case when country_code='US'
        then
            case when p.kind_code like 'S%'
                then p.publication_date + interval '14 years'
                else coalesce(p.priority_date, p.filing_date) + interval '1' day * coalesce(pair.term_adjustments,0) + interval '20 years'
            end
        else null
    end,
    pair.term_adjustments
    from patents_global as p
    left outer join big_query_pair_by_pub as pair
        on (p.publication_number_full=pair.publication_number_full)
);
