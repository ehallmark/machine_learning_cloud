\connect patentdb

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

create table big_query_assignment_documentid_family_id (
    doc_number varchar(32) not null,
    is_filing boolean not null,
    family_id varchar(32) not null,
    reel_frames varchar(50)[] not null,
    primary key(doc_number,is_filing)
);

insert into big_query_assignment_documentid_family_id (doc_number,is_filing,family_id,reel_frames) (
    select doc_number,is_filing,mode() within group(order by family_id),array_agg(distinct reel_frame) from big_query_assignment_documentid as a
    inner join patents_global as p on ((p.country_code='US') AND ((p.publication_number=a.doc_number AND not a.is_filing) OR (p.application_number_formatted = a.doc_number AND a.is_filing)))
    where p.country_code = 'US' and family_id!='-1'
    group by (doc_number,is_filing)
);

create index big_query_assignment_documentid_family_id_idx on (big_query_assignment_documentid_family_id) (doc_number,is_filing,family_id);

