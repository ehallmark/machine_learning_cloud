\connect patentdb

update big_query_assignments set assignee = '{}'::varchar[] where assignee[1] is null;

drop table big_query_patent_to_latest_assignee;
create table big_query_patent_to_latest_assignee (
    application_number_formatted_with_country varchar(32) primary key, -- eg. US9923222B1
    assignee text[] not null,
    date date
);
-- ingest assignment table
insert into big_query_patent_to_latest_assignee (
    select distinct on (application_number_formatted_with_country) application_number_formatted_with_country,assignee,execution_date from (
        select
        application_number_formatted_with_country,
        assignee,
        execution_date,
        recorded_date
        from big_query_assignments as latest
        join big_query_assignment_documentid as document
        on (latest.reel_frame=document.reel_frame)
        where assignee is not null and array_length(assignee, 1) > 0 and not upper(coalesce(conveyance_text,'')) like '%SECURITY%'
    ) as temp order by application_number_formatted_with_country,execution_date desc nulls last,recorded_date desc NULLS LAST
);

drop table big_query_patent_to_security_interest;
create table big_query_patent_to_security_interest (
    application_number_formatted_with_country varchar(32) primary key, -- eg. US9923222B1
    security_interest_holder text[] not null,
    date date
);
insert into big_query_patent_to_security_interest (
    select distinct on (application_number_formatted_with_country)
        application_number_formatted_with_country,
        assignee,
        execution_date
        from big_query_assignments as latest
        join big_query_assignment_documentid as document
        on (latest.reel_frame=document.reel_frame)
        where assignee is not null and array_length(assignee, 1) > 0 and upper(coalesce(conveyance_text,'')) like '%SECURITY%'
        order by application_number_formatted_with_country,execution_date desc nulls last,recorded_date desc NULLS LAST
);


-- latest assignee data by publication number full
drop table big_query_patent_to_latest_assignee_by_pub;
create table big_query_patent_to_latest_assignee_by_pub (
    publication_number_full varchar(32) primary key,
    first_assignee text not null,
    assignee text[] not null,
    date date
);

insert into big_query_patent_to_latest_assignee_by_pub (
    select distinct on (publication_number_full) publication_number_full,(case when la.assignee is not null and array_length(la.assignee, 1) > 0 then la.assignee else p.assignee_harmonized end)[1],(case when la.assignee is not null and array_length(la.assignee, 1) > 0 then la.assignee else p.assignee_harmonized end),coalesce(la.date,case when la.assignee is null OR array_length(la.assignee, 1)=0 then coalesce(p.priority_date,p.filing_date) else null end)
    from patents_global as p
    left outer join big_query_patent_to_latest_assignee as la
    on ('US'||p.application_number_formatted='US'||la.application_number_formatted_with_country)
    where (case when la.assignee is not null and array_length(la.assignee, 1) > 0 then la.assignee else p.assignee_harmonized end)[1] is not null and p.application_number_formatted is not null
    order by publication_number_full,date desc nulls last,publication_date desc nulls last
);
create index big_query_latest_by_pub_first_assignee_idx on big_query_patent_to_latest_assignee_by_pub (first_assignee);

-- security interest data by publication number full
drop table big_query_patent_to_security_interest_by_pub;
create table big_query_patent_to_security_interest_by_pub (
    publication_number_full varchar(32) primary key,
    security_interest_holder text not null,
    date date
);
insert into big_query_patent_to_security_interest_by_pub (
    select distinct on (publication_number_full) publication_number_full,si.security_interest_holder[1],si.date
    from patents_global as p
    left outer join big_query_patent_to_security_interest as si
    on ('US'||p.application_number_formatted='US'||si.application_number_formatted_with_country)
    where si.security_interest_holder is not null and si.security_interest_holder[1] is not null and p.application_number_formatted is not null
    order by publication_number_full,date desc nulls last,publication_date desc nulls last
);

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
    select name,mode() within group (order by name_cc),count(*),mode() within group (order by original_entity_type), max(filing_date),min(filing_date)
    from (
        select a.first_assignee as name, t.country_code as name_cc, m.original_entity_status as original_entity_type,filing_date
        from patents_global as t
        left join big_query_maintenance_by_pub as m on (t.publication_number_full=m.publication_number_full)
        left join big_query_patent_to_latest_assignee_by_pub as a on (a.publication_number_full=t.publication_number_full)
        where a.first_assignee is not null
    ) as temp
    group by name
);

create index big_query_assignee_lower_name_idx on big_query_assignee (lower(name));
create index big_query_assignee_portfolio_size_idx on big_query_assignee (portfolio_size);


-- aggregations (additional)
drop table big_query_patent_to_latest_assignee_join_by_pub;
create table big_query_patent_to_latest_assignee_join_by_pub (
    publication_number_full varchar(32) primary key,
    first_assignee text not null,
    assignee text[] not null,
    date date,
    portfolio_size integer,
    entity_type varchar(32),
    first_filing_date date,
    last_filing_date date
);

insert into big_query_patent_to_latest_assignee_join_by_pub (
    select
       latest_assignee.publication_number_full,
       latest_assignee.first_assignee,
       latest_assignee.assignee,
       latest_assignee.date,
       latest_assignee_join.portfolio_size,
       latest_assignee_join.entity_type,
       latest_assignee_join.first_filing_date,
       latest_assignee_join.last_filing_date
    from big_query_patent_to_latest_assignee_by_pub as latest_assignee
    left outer join big_query_assignee as latest_assignee_join on (latest_assignee_join.name=latest_assignee.first_assignee)
);