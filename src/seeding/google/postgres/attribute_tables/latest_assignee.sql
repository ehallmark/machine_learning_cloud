\connect patentdb

create table big_query_patent_to_latest_assignee (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32) not null,
    name text not null,
    country_code varchar(8) not null,
    date date not null
);
create index big_query_latest_assignee_name_idx on big_query_patent_to_latest_assignee (name);
create index big_query_latest_assignee_family_id_idx on big_query_patent_to_latest_assignee (family_id);