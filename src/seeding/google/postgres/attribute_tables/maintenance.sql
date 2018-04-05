\connect patentdb

create table big_query_patent_to_maintenance (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32) not null,
    code varchar(32) not null
);
create index big_query_patent_to_maintenance_code_idx on big_query_patent_to_maintenance (code);
create index big_query_patent_to_maintenance_family_id_idx on big_query_patent_to_maintenance (family_id);