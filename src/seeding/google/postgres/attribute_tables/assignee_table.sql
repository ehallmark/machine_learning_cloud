
\connect patentdb

create table big_query_assignee (
    name varchar(32) primary key,
    country_code varchar(8),
    portfolio_size integer,
    entity_type varchar(32),
    last_filing_date date,
    first_filing_date date
);

insert into big_query_assignee (name,country_code,portfolio_size,last_filing,first_filing_date)
(
    select name,mode() within group (order by name_cc),count(distinct family_id),mode() within group (order by original_entity_type), max(filing_date),min(filing_date)
    from (
        select temp2.assignee_harmonized as name, temp2.assignee_harmonized_cc as name_cc,family_id,original_entity_type,filing_date
        from patents_global as t,
        unnest(t.assignee_harmonized,t.assignee_harmonized_cc) as temp
    ) as temp
    group by name
);