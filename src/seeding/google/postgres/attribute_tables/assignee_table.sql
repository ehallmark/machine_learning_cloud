
\connect patentdb

create table big_query_assignee (
    name varchar(32) primary key,
    country_code varchar(8),
    portfolio_size integer,

);
create index big_query_patent_to_embedding_family_id_idx on big_query_patent_to_embedding (family_id);