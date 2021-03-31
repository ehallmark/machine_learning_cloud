\connect patentdb

drop table big_query_assignment_documentid;
drop table big_query_assignments;
create table big_query_assignments (
    reel_frame varchar(50) primary key,
    conveyance_text text not null,
    recorded_date date not null,
    execution_date date,
    assignee text[] not null,
    assignor text[] not null
);

drop table big_query_assignment_documentid;
create table big_query_assignment_documentid (
    reel_frame varchar(50) references big_query_assignments (reel_frame),
    application_number_formatted_with_country varchar(32) not null, -- really should be application_number_formatted :(
    date date, -- could be useful for matching
    primary key (reel_frame,application_number_formatted_with_country)
);


create index big_query_assignment_documentid_doc_number_idx on big_query_assignment_documentid (application_number_formatted_with_country);
