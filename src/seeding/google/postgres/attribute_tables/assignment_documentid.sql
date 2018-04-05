\connect patentdb

create table big_query_assignment_documentid (
    reel_frame varchar(50) references big_query_assignments (reel_frame),
    publication_number varchar(32),
    publication_kind varchar(8),
    application_number_formatted varchar(32),
    application_kind varchar(8),

    constraint big_query_assignment_documentid_id
        unique (reel_frame,publication_number,application_number)
);

create index big_query_assignment_documentid_publication_number_idx on big_query_assignment_documentid (publication_number);
create index big_query_assignment_documentid_application_number_formatted_idx on big_query_assignment_documentid (application_number_formatted);