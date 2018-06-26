\connect patentdb

drop table big_query_patent_to_latest_assignee;
create table big_query_patent_to_latest_assignee (
    application_number_formatted_with_country varchar(32) primary key, -- eg. US9923222B1
    assignee text[] not null,
    date date
);
-- ingest assignment table
insert into big_query_patent_to_latest_assignee (
    select distinct on (application_number_formatted_with_country)
        application_number_formatted_with_country,
        assignee,
        execution_date
        from big_query_assignments as latest
        join big_query_assignment_documentid as document
        on (latest.reel_frame=document.reel_frame)
        where assignee is not null and not upper(coalesce(conveyance_text,'')) like '%SECURITY%'
        order by application_number_formatted_with_country,execution_date desc nulls last,recorded_date desc NULLS LAST
);

drop table big_query_patent_to_security_interest;
create table big_query_patent_to_security_interest (
    application_number_formatted_with_country varchar(32) primary key, -- eg. US9923222B1
    security_interest_holder text[] not null,
    date date
);
insert into big_query_patent_to_security_interest (
    select application_number_formatted_with_country,assignee,execution_date from (
        select distinct on (application_number_formatted_with_country)
            application_number_formatted_with_country,
            assignee,
            execution_date
            from big_query_assignments as latest
            join big_query_assignment_documentid as document
            on (latest.reel_frame=document.reel_frame)
            where assignee is not null and upper(coalesce(conveyance_text,'')) like '%SECURITY%'
            order by application_number_formatted_with_country,execution_date desc nulls last,recorded_date desc NULLS LAST
    ) as temp
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
    select distinct on (publication_number_full) publication_number_full,(coalesce(la.assignee,p.assignee_harmonized))[1],coalesce(la.assignee,p.assignee_harmonized),coalesce(la.date,case when la.assignee is null then coalesce(p.priority_date,p.filing_date) else null end)
    from patents_global as p
    left outer join big_query_patent_to_latest_assignee as la
    on ('US'||p.application_number_formatted='US'||la.application_number_formatted_with_country)
    where (coalesce(la.assignee,p.assignee_harmonized))[1] is not null and p.application_number_formatted is not null
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
    on ('US'||p.application_number_formatted=si.'US'||application_number_formatted_with_country)
    where si.security_interest_holder is not null and si.security_interest_holder[1] is not null and p.application_number_formatted is not null
    order by publication_number_full,date desc nulls last,publication_date desc nulls last
);

-- latest assignee data by family id
drop table big_query_patent_to_latest_assignee_by_family;
create table big_query_patent_to_latest_assignee_by_family (
    family_id varchar(32) primary key,
    first_assignee text not null,
    assignee text[] not null,
    date date
);

insert into big_query_patent_to_latest_assignee_by_family (
    select distinct on (family_id) family_id,(coalesce(la.assignee,p.assignee_harmonized))[1],coalesce(la.assignee,p.assignee_harmonized),coalesce(la.date,case when la.assignee is null then coalesce(p.priority_date,p.filing_date) else null end)
    from patents_global as p
    left outer join big_query_patent_to_latest_assignee as la
    on ('US'||p.application_number_formatted='US'||la.application_number_formatted_with_country)
    where family_id!='-1' and (coalesce(la.assignee,p.assignee_harmonized))[1] is not null and p.application_number_formatted is not null
    order by family_id,date desc nulls last,publication_date desc nulls last
);
create index big_query_latest_by_family_first_assignee_idx on big_query_patent_to_latest_assignee_by_family (first_assignee);


-- security interest data by family id
drop table big_query_patent_to_security_interest_by_fam;
create table big_query_patent_to_security_interest_by_fam (
    publication_number_full varchar(32) primary key,
    security_interest_holder text not null,
    date date
);
insert into big_query_patent_to_security_interest_by_fam (
    select distinct on (family_id) family_id,si.security_interest_holder[1],si.date
    from patents_global as p
    left outer join big_query_patent_to_security_interest as si
    on ('US'||p.application_number_formatted='US'||si.application_number_formatted_with_country)
    where family_id!='-1' and si.security_interest_holder is not null and si.security_interest_holder[1] is not null and p.application_number_formatted is not null
    order by family_id,date desc nulls last,publication_date desc nulls last
);
