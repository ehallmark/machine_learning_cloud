\connect patentdb

create table big_query_assignment_assignees (
    reel_frame varchar(50) references big_query_assignments (reel_frame),
    name text not null,
    country text
);
