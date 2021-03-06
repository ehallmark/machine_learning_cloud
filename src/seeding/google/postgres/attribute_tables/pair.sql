\connect patentdb

create table big_query_pair (
    application_number_formatted varchar(32) primary key,
    publication_number varchar(32),
    original_entity_type varchar(32),
    status text,
    status_date date,
    abandoned boolean, -- from status, whether app was abandoned
    term_adjustments integer
);


create table big_query_pta (
    application_number_formatted varchar(32) primary key,
    term_adjustments integer not null
);

