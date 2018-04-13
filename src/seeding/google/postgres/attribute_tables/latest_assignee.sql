\connect patentdb

create table big_query_patent_to_latest_assignee (
    doc_number varchar(32) not null, -- eg. US9923222B1
    doc_kind varchar(8) not null,
    is_filing boolean not null,
    country_code varchar(8),
    assignee text[] not null,
    date date,
    security_interest boolean,
    primary key (doc_number,is_filing)
);

-- ingest assignment table
insert into big_query_patent_to_latest_assignee (doc_number,doc_kind,is_filing,country_code,assignee,date,security_interest) (
    select distinct on (doc_number,doc_kind='X0')
        doc_number,
        doc_kind,
        doc_kind='X0',
        country_code,
        assignee,
        execution_date,
        upper(coalesce(conveyance_text,'')) like '%SECURITY%'
        from big_query_assignments as latest
        join big_query_assignment_documentid as document
        on (latest.reel_frame=document.reel_frame)
        where assignee is not null
        order by doc_number,doc_kind='X0',execution_date desc nulls last,recorded_date desc NULLS LAST
);

-- definite
create table big_query_patent_to_latest_assignee_by_pub (
    publication_number_full varchar(32) primary key,
    first_assignee text not null,
    assignee text[] not null,
    date date,
    security_interest boolean not null
);

insert into big_query_patent_to_latest_assignee_by_pub (publication_number_full,first_assignee,assignee,date,security_interest) (
    select distinct on (publication_number_full) publication_number_full,(coalesce(la.assignee,p.assignee))[1],coalesce(la.assignee,p.assignee),coalesce(la.date,case when la.assignee is null then coalesce(p.priority_date,p.filing_date) else null end),coalesce(la.security_interest,'f')
    from patents_global as p
    left outer join big_query_patent_to_latest_assignee as la
    on (p.country_code='US' and((p.publication_number=la.doc_number and not la.is_filing) OR (p.application_number=la.doc_number and la.is_filing)))
    where family_id!='-1'
    order by publication_number_full,date desc nulls last,publication_date desc nulls last
);


create index big_query_latest_by_pub_first_assignee_idx on big_query_patent_to_latest_assignee_by_pub (first_assignee);


-- good guess
create table big_query_patent_to_latest_assignee_by_family (
    family_id varchar(32) primary key,
    first_assignee text not null,
    assignee text[] not null,
    date date,
    security_interest boolean not null
);

insert into big_query_patent_to_latest_assignee_by_family (family_id,first_assignee,assignee,date,security_interest) (
    select distinct on (family_id) family_id,(coalesce(la.assignee,p.assignee))[1],coalesce(la.assignee,p.assignee),coalesce(la.date,case when la.assignee is null then coalesce(p.priority_date,p.filing_date) else null end),coalesce(la.security_interest,'f')
    from patents_global as p
    left outer join big_query_patent_to_latest_assignee as la
    on (p.country_code='US' and((p.publication_number=la.doc_number and not la.is_filing) OR (p.application_number=la.doc_number and la.is_filing)))
    where family_id!='-1'
    order by family_id,date desc nulls last,publication_date desc nulls last
);

create index big_query_latest_by_family_first_assignee_idx on big_query_patent_to_latest_assignee_by_family (first_assignee);
