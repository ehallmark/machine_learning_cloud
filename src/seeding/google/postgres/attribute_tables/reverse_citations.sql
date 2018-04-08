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
        coalesce(cited_publication_number_full,cited_application_number_full),
        cited_publication_number_full is null
        array_agg(publication_number_full),
        array_agg(application_number_full),
        array_agg(family_id)
        array_agg(cited_type),
        array_agg(cited_category)
        array_agg(filing_date)
    from (select publication_number_full,application_number_full,family_id,unnest(cited_publication_number_full) as cited_publication_number_full,unnest(cited_application_number_full) as cited_application_number_full,unnest(cited_type) as cited_type,unnest(cited_category) as cited_category
        from patents_global
    ) as temp  where cited_application_number_full is not null OR cited_publication_number_full is not null
    group by (coalesce(cited_publication_number_full,application_number_full),cited_publication_number_full is null)
);