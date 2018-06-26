\connect patentdb

drop table big_query_assignment_documentid;
drop table big_query_assigments;
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

drop table big_query_assignment_documentid_by_pub;
create table big_query_assignment_documentid_by_pub (
    publication_number_full varchar(32) primary key,
    reel_frame varchar(50)[] not null,
    conveyance_text text[] not null,
    execution_date date[] not null,
    recorded_date date[] not null,
    assignee text[] not null,
    assignor text[] not null -- first assignor of each reel frame only
);

insert into big_query_assignment_documentid_by_pub (publication_number_full,reel_frame,conveyance_text,execution_date,recorded_date,assignee,assignor) (
    select publication_number_full,
        array_agg(r.reel_frame) as reel_frame,
        array_agg(r.conveyance_text) as conveyance_text,
        array_agg(r.execution_date) as execution_date,
        array_agg(r.recorded_date) as recorded_date,
        array_agg(r.assignee[1]) as assignee,
        array_agg(r.assignor[1]) as assignor
    from big_query_assignment_documentid as d
    join big_query_assignments as r on (d.reel_frame=r.reel_frame)
    join patents_global as p on ('US'||p.application_number_formatted='US'||d.application_number_formatted_with_country)
    where array_length(r.assignee,1)>0 and p.application_number_formatted is not null
    group by publication_number_full
);

