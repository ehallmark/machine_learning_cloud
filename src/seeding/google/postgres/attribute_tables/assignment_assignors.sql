\connect patentdb

create table big_query_assignment_assignors (
    reel_frame varchar(50) not null references big_query_assignments (reel_frame),
    name text not null,

    CONSTRAINT big_query_assignment_assignors_id
        primary key (reel_frame,name);
);
