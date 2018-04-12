\connect patentdb

create table big_query_reverse_citations (
    doc_number_full varchar(32) not null,
    is_filing boolean not null,
    rcite_publication_number_full varchar(32)[] not null,
    rcite_application_number_full varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_filing_date date[] not null,
    primary key (doc_number_full,is_filing)
);


insert into big_query_reverse_citations (doc_number_full,is_filing,rcite_publication_number_full,rcite_application_number_full,rcite_family_id,rcite_filing_date) (
    select
        coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),
        temp.cited_publication_number_full is null,
        (array_agg(publication_number_full))[1:10000], -- limit rcites
        (array_agg(application_number_full))[1:10000], -- limit rcites
        (array_agg(family_id))[1:10000], -- limit rcites
        (array_agg(filing_date))[1:10000] -- limit rcites
    from patents_global as t, unnest(t.cited_publication_number_full,t.cited_application_number_full) with ordinality as temp(cited_publication_number_full,cited_application_number_full,n)
    where (temp.cited_application_number_full is not null OR temp.cited_publication_number_full is not null)
    group by (coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),temp.cited_publication_number_full is null)
);


create table big_query_reverse_citations_family_id (
    doc_number_full varchar(32) not null,
    is_filing boolean not null,
    family_id varchar(32) not null,
    primary key (doc_number_full,is_filing)
);

insert into big_query_reverse_citations_family_id (doc_number_full,is_filing,family_id) (
    select doc_number_full,is_filing,family_id from big_query_reverse_citations as c
    inner join patents_global as p on ((p.publication_number_full=c.doc_number_full AND not c.is_filing) OR (p.application_number_full=c.doc_number_full AND c.is_filing))
);
create index big_query_reverse_citations_family_id_idx on big_query_reverse_citations_family_id (doc_number_full,is_filing,family_id);
