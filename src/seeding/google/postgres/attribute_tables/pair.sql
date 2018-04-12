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

create table big_query_pair_family_id (
    application_number_formatted varchar(32) primary key,
    family_id varchar(32) not null
);

insert into big_query_pair_family_id (application_number_formatted,family_id) (

);

create index big_query_pair_family_id_idx on big_query_pair_family_id(application_number_formatted,family_id);

