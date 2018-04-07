
\connect patentdb

create table big_query_patent_to_priority_claims (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32) not null,
    pc_publication_number_full varchar(32) not null,
    pc_application_number_full varchar(32) not null,
    pc_filing_date date
);
create index big_query_patent_to_priority_claims_pc_publication_number_full_idx on big_query_patent_to_priority_claims (pc_publication_number_full);
create index big_query_patent_to_priority_claims_pc_application_number_full_idx on big_query_patent_to_priority_claims (pc_application_number_full);
create index big_query_patent_to_priority_claims_family_id_idx on big_query_patent_to_priority_claims (family_id);

