\connect patentdb

drop table big_query_assignee;
create table big_query_assignee (
    name varchar(256) primary key,
    country_code varchar(8),
    portfolio_size integer,
    entity_type varchar(32),
    last_filing_date date,
    first_filing_date date
);

insert into big_query_assignee (name,country_code,portfolio_size,entity_type,last_filing_date,first_filing_date)
(
    select name,mode() within group (order by name_cc),count(distinct family_id),mode() within group (order by original_entity_type), max(filing_date),min(filing_date)
    from (
        select temp.assignee_harmonized as name, temp.assignee_harmonized_cc as name_cc,family_id,coalesce(m.original_entity_status,pair.original_entity_type) as original_entity_type,filing_date
        from patents_global as t
        left join big_query_pair_by_pub as pair on (t.publication_number_full=pair.publication_number_full)
        left join big_query_maintenance_by_pub as m on (t.publication_number_full=m.publication_number_full),
        unnest(t.assignee_harmonized,t.assignee_harmonized_cc) with ordinality as temp(assignee_harmonized,assignee_harmonized_cc,n)

    ) as temp
    group by name
);

create index big_query_assignee_lower_name_idx on big_query_assignee (lower(name));
create index big_query_assignee_portfolio_size_idx on big_query_assignee (portfolio_size);