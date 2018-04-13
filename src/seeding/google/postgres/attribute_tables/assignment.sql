\connect patentdb

create table big_query_assignments (
    reel_frame varchar(50) primary key,
    conveyance_text text not null,
    recorded_date date not null,
    execution_date date,
    assignee text[] not null,
    assignor text[] not null
);


create table big_query_assignment_documentid (
    reel_frame varchar(50) references big_query_assignments (reel_frame),
    doc_number varchar(32) not null,
    doc_kind varchar(8) not null,
    is_filing boolean not null, -- convenience field
    country_code varchar(8) not null default('US'),
    date date, -- could be useful for matching
    primary key (reel_frame,doc_number)
);

create index big_query_assignment_documentid_doc_number_idx on big_query_assignment_documentid (doc_number);
create index big_query_assignment_documentid_is_filing_idx on big_query_assignment_documentid (is_filing);
--create index big_query_merge_helper_idx on big_query_assignment_documentid (country_code,publication_number);
--create index big_query_merge_helper_idx2 on big_query_assignment_documentid (country_code,application_number_formatted);

create table big_query_assignment_documentid_by_pub (
    publication_number_full varchar(32) primary key,
    reel_frame varchar(50)[] not null,
    conveyance_text text[] not null,
    execution_date date[] not null,
    recorded_date date[] not null,
    assignee text[][] not null,
    assignor text[][] not null -- first assignor of each reel frame only
);

insert into big_query_assignment_documentid_by_pub (publication_number_full,reel_frame,conveyance_text,execution_date,recorded_date,assignee,assignor) (
    select distinct on (publication_number_full) publication_number_full,a.reel_frame,a.conveyance_text,execution_date,a.recorded_date,a.assignee,a.assignor
    from (
        select doc_number,array_agg(r.reel_frame) as reel_frame,array_agg(r.conveyance_text) as conveyance_text,array_agg(r.execution_date) as execution_date,array_agg(r.recorded_date) as recorded_date,array_agg(r.assignee) as assignee,array_agg(r.assignor) as assignor
        from big_query_assignment_documentid as d
        join big_query_assignments as r on (d.reel_frame=r.reel_frame)
        where is_filing
        group by doc_number
    ) as a
    inner join patents_global as p on ((p.country_code='US') AND ((p.application_number_formatted = a.doc_number)))
    where p.country_code = 'US' and p.application_number_formatted is not null and family_id!='-1'
    order by publication_number_full,publication_date desc nulls last
);

