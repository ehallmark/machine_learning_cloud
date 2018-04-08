\connect patentdb

create table big_query_assignment_documentid (
    reel_frame varchar(50) references big_query_assignments (reel_frame),
    doc_number varchar(32) not null,
    doc_kind varchar(8) not null,
    is_filing boolean not null, -- convenience field
    country_code varchar(8) not null default('US'),
    date date -- could be useful for matching

    constraint big_query_assignment_documentid_id
        primary key (reel_frame,doc_number)
);

create index big_query_assignment_documentid_doc_number_idx on big_query_assignment_documentid (doc_number);
