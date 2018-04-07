
\connect patentdb

create table big_query_patent_to_citations (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32) not null,
    cited_publication_number_full varchar(32),
    cited_application_number_full varchar(32),
    cited_npl_text text,
    cited_type varchar(32),
    cited_category varchar(32),
    cited_filing_date date
);
create index big_query_citations_cited_publication_number_full_idx on big_query_patent_to_citations (cited_publication_number_full);
create index big_query_citations_cited_application_number_full_idx on big_query_patent_to_citations (cited_application_number_full);
create index big_query_citations_family_id_idx on big_query_patent_to_citations (family_id);

