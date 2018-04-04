
\connect patentdb

create table big_query_patent_to_patent_family (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    publication_number varchar(32) not null, -- eg. US9923222
    application_number_full varchar(32) not null,
    application_number varchar(32), -- eg. US20012322342
    country_code varchar(8),
    kind_code varchar(8),
    application_kind varchar(8),
    family_id varchar(32) not null
);
create index big_query_patent_family_publication_number_idx on big_query_patent_to_patent_family (publication_number);
create index big_query_patent_family_application_number_full_idx on big_query_patent_to_patent_family (application_number_full);
create index big_query_patent_family_family_id_idx on big_query_patent_to_patent_family (family_id);