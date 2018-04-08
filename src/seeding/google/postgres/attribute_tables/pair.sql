\connect patentdb

create table big_query_pair (
    application_number_formatted varchar(32) primary key,
    publication_number varchar(32),
    original_entity_type varchar(32),
    status text not null,
    status_date date not null,
    abandoned boolean, -- from status, whether app was abandoned
    term_adjustments integer
);

create index big_query_assignment_documentid_doc_number_idx on big_query_assignment_documentid (doc_number);
