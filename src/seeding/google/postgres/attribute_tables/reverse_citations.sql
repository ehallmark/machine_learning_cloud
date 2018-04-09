\connect patentdb

create table big_query_reverse_citations (
    doc_number_full varchar(32) not null,
    is_filing boolean not null,
    rcite_publication_number_full varchar(32)[] not null,
    rcite_application_number_full varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_type varchar(32)[] not null,
    rcite_category varchar(8)[] not null,
    rcite_filing_date date[] not null,
    primary key (doc_number_full,is_filing)
);


insert into big_query_reverse_citations (doc_number_full,is_filing,rcite_publication_number_full,rcite_application_number_full,rcite_family_id,rcite_type,rcite_category,rcite_filing_date) (
    select
        coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),
        temp.cited_publication_number_full is null,
        array_agg(publication_number_full),
        array_agg(application_number_full),
        array_agg(family_id),
        array_agg(temp.cited_type),
        array_agg(temp.cited_category),
        array_agg(filing_date)
    from patents_global as t, unnest(t.cited_publication_number_full,t.cited_application_number_full,t.cited_type,t.cited_category) with ordinality as temp(cited_publication_number_full,cited_application_number_full,cited_type,cited_category,n)
    where t.cited_application_number_full is not null OR t.cited_publication_number_full is not null
    group by (coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),temp.cited_publication_number_full is null)
);