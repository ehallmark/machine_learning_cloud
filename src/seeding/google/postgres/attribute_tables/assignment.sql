\connect patentdb

create table big_query_assignments (
    reel_frame varchar(50) primary key,
    conveyance_text text not null,
    recorded_date date not null,
    execution_date date,
    assignee text[] not null,
    assignor text[] not null
);
