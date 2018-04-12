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
    select distinct on (doc_number,doc_kind)
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
        order by doc_number,doc_kind,execution_date,recorded_date desc NULLS LAST
);

