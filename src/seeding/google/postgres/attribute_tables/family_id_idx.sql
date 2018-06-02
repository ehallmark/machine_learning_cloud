\connect patentdb

-- helper table for user interface to quickly find family members
drop table big_query_family_id;
create table big_query_family_id (
    publication_number_full varchar(32) primary key,
    publication_number_with_country varchar(32) not null,
    publication_number varchar(32) not null,
    application_number_full varchar(32),
    application_number_with_country varchar(32),
    application_number varchar(32),
    application_number_formatted varchar(32),
    application_number_formatted_with_country varchar(32),
    family_id varchar(32)
);

-- must run this after patents_global merged
insert into big_query_family_id (publication_number_full,publication_number_with_country,
        publication_number,application_number_full,application_number_with_country,
        application_number,application_number_formatted,application_number_formatted_with_country,
        family_id) (
    select publication_number_full,publication_number_with_country,publication_number,
        application_number_full,application_number_with_country,application_number,
        application_number_formatted,application_number_formatted_with_country,
        family_id
    from patents_global_merged
);


create index big_query_family_id_fam_id on big_query_family_id (family_id);
create index big_query_family_id_pub_num_with_country on big_query_family_id (publication_number_with_country);
create index big_query_family_id_pub_num on big_query_family_id (publication_number);
create index big_query_family_id_app_num_full on big_query_family_id (application_number_full);
create index big_query_family_id_app_num_with_country on big_query_family_id (application_number_formatted_with_country);
create index big_query_family_id_app_num on big_query_family_id (application_number);
create index big_query_family_id_app_num_f on big_query_family_id (application_number_formatted);
create index big_query_family_id_app_num_f_with_country on big_query_family_id (application_number_formatted_with_country);

