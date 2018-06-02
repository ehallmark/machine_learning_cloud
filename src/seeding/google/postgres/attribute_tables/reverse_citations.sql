\connect patentdb

drop table big_query_reverse_citations;
create table big_query_reverse_citations (
    doc_number_full varchar(32) not null,
    is_filing boolean not null,
    rcite_publication_number_full varchar(32)[] not null,
    rcite_application_number_full varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_filing_date date[] not null,
    rcite_type varchar(32)[] not null,
    rcite_category varchar(32)[] not null,
    primary key (doc_number_full,is_filing)
);


insert into big_query_reverse_citations (doc_number_full,is_filing,rcite_publication_number_full,rcite_application_number_full,rcite_family_id,rcite_filing_date,rcite_type,rcite_category) (
    select
        coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),
        temp.cited_publication_number_full is null,
        (array_agg(publication_number_full))[1:10000], -- limit rcites
        (array_agg(application_number_full))[1:10000], -- limit rcites
        (array_agg(case when family_id='-1' then null else family_id end))[1:10000], -- limit rcites
        (array_agg(filing_date))[1:10000], -- limit rcites
        (array_agg(temp.cited_type))[1:10000],
        (array_agg(temp.cited_category))[1:10000]
    from patents_global as t, unnest(t.cited_publication_number_full,t.cited_application_number_full,t.cited_type,t.cited_category) with ordinality as temp(cited_publication_number_full,cited_application_number_full,cited_type,cited_category,n)
    where (temp.cited_application_number_full is not null OR temp.cited_publication_number_full is not null)
    group by (coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),temp.cited_publication_number_full is null)
);

drop table big_query_reverse_citations_by_pub;
create table big_query_reverse_citations_by_pub (
    publication_number_full varchar(32) primary key,
    rcite_publication_number_full varchar(32)[] not null,
    rcite_application_number_full varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_filing_date date[] not null,
    rcite_type varchar(32)[] not null,
    rcite_category varchar(32)[] not null
);


insert into big_query_reverse_citations_by_pub (publication_number_full,rcite_publication_number_full,rcite_application_number_full,rcite_family_id,rcite_filing_date,rcite_type,rcite_category) (
    select publication_number_full,c.rcite_publication_number_full,c.rcite_application_number_full,c.rcite_family_id,c.rcite_filing_date,c.rcite_type,c.rcite_category
    from big_query_reverse_citations as c
    inner join patents_global as p on ((p.publication_number_full=c.doc_number_full and not c.is_filing) OR (p.application_number_full=c.doc_number_full and c.is_filing))
);


