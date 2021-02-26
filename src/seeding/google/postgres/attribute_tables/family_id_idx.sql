\connect patentdb

-- helper table for user interface to quickly find family members
create table big_query_family_id2 (
    publication_number_full varchar(32) primary key,
    publication_number_with_country varchar(32) not null,
    publication_number varchar(32) not null,
    application_number_full varchar(32),
    application_number_with_country varchar(32),
    application_number varchar(32),
    application_number_formatted varchar(32),
    application_number_formatted_with_country varchar(32),
    kind_code varchar(8),
    family_id varchar(32)
);

insert into big_query_family_id2 (publication_number_full,publication_number_with_country,
        publication_number,application_number_full,application_number_with_country,
        application_number,application_number_formatted,application_number_formatted_with_country,
        family_id, kind_code) (
    select publication_number_full,country_code||publication_number,publication_number,
        application_number_full,case when application_number is null then null else country_code||application_number end,application_number,
        application_number_formatted,case when application_number_formatted is null then null else country_code||application_number_formatted end,
        family_id, kind_code
    from patents_global
);

-- update table name
drop table if exists big_query_family_id;
alter table big_query_family_id2 rename to big_query_family_id;

create index big_query_family_id_fam_id2 on big_query_family_id (family_id);
create index big_query_family_id_pub_num_with_country2 on big_query_family_id (publication_number_with_country);
create index big_query_family_id_pub_num2 on big_query_family_id (publication_number);
create index big_query_family_id_app_num_full2 on big_query_family_id (application_number_full);
create index big_query_family_id_app_num_with_country2 on big_query_family_id (application_number_formatted_with_country);
create index big_query_family_id_app_num2 on big_query_family_id (application_number);
create index big_query_family_id_app_num_f2 on big_query_family_id (application_number_formatted);
create index big_query_family_id_app_num_f_with_country2 on big_query_family_id (application_number_formatted_with_country);

drop table big_query_family_counterparts;
create table big_query_family_counterparts (
    family_id varchar(32) primary key,
    counterpart_publication_number_full text[] not null,
    counterpart_publication_number_with_country text[] not null,
    counterpart_publication_number text[] not null,
    counterpart_application_number_formatted_with_country text[] not null,
    counterpart_application_number_formatted text[] not null,
    counterpart_country text[] not null,
    counterpart_kind text[] not null
);

insert into big_query_family_counterparts (
    select family_id,
    array_agg(publication_number_full),
    array_agg(publication_number_with_country),
    array_agg(publication_number),
    array_agg(application_number_formatted_with_country),
    array_agg(application_number_formatted),
    array_agg(substring(publication_number_full from 1 for 2)),
    array_agg(kind_code)
    from big_query_family_id
    where family_id != '-1'
    group by family_id
);

