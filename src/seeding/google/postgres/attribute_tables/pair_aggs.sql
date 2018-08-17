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
     p.publication_number_full, pair.original_entity_type, status, status_date, abandoned, term_adjustments
    from big_query_pair as pair
    inner join patents_global as p on (p.application_number_formatted=pair.application_number_formatted AND p.country_code='US')
    where p.country_code='US' and p.application_number_formatted is not null and family_id!='-1'
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

drop table big_query_international_priority;
create table big_query_international_priority (
    family_id varchar(32) primary key,
    priority_date date not null
);


insert into big_query_international_priority (
    select p.family_id,
    min(case when p.kind_code='WO' then coalesce(p.priority_date,p.filing_date) else null end)
    from patents_global as p
    where p.family_id is not null and p.family_id!='-1'
    group by p.family_id
    having bool_or(p.kind_code='WO')
);


insert into big_query_priority_and_expiration (
    select p.publication_number_full,
    coalesce(p.priority_date, p.filing_date),
    coalesce(p.priority_date, p.filing_date) + interval '1' day * coalesce(pair.term_adjustments,0),
    case when country_code='US'
        then
            case when p.kind_code like 'S%'
                then p.publication_date + interval '14 years'
                else coalesce(pct.priority_date, coalesce(p.priority_date, p.filing_date)) + interval '1' day * coalesce(pair.term_adjustments,0) + interval '20 years'
            end
        else null
    end,
    pair.term_adjustments
    from patents_global as p
    left outer join big_query_pair_by_pub as pair
        on (p.publication_number_full=pair.publication_number_full)
    left outer join big_query_international_priority as pct
        on (p.family_id=pct.family_id)

) on conflict do nothing;
