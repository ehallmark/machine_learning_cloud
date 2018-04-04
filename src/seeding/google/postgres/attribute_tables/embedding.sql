
\connect patentdb

create table big_query_patent_to_embedding (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32) not null,
    embedding float[] not null
);
create index big_query_patent_to_embedding_family_id_idx on big_query_patent_to_embedding (family_id);