\connect patentdb

drop table big_query_citations_helper;
create table big_query_citations_helper (
    publication_number_full varchar(32) not null,
    num integer not null,
    cited_publication_number_full varchar(32),
    cited_publication_number_with_country varchar(32),
    cited_publication_number varchar(32),
    cited_application_number_formatted_with_country varchar(32),
    cited_application_number_formatted varchar(32),
    cited_npl_text text,
    cited_type varchar(32),
    cited_category varchar(32),
    cited_family_id varchar(32),
    cited_filing_date date,
    primary key(publication_number_full, num)
);

insert into big_query_citations_helper (
    select p.publication_number_full,
    num,
    t.cited_publication_number_full,
    p2.country_code||p2.publication_number,
    p2.publication_number,
    p2.country_code||p2.application_number_formatted,
    p2.application_number_formatted,
    t.cited_npl_text,
    t.cited_type,
    t.cited_category,
    case when p2.family_id ='-1' then null else p2.family_id end,
    t.cited_filing_date
    from patents_global as p, unnest(p.cited_publication_number_full,p.cited_application_number_full,p.cited_npl_text,p.cited_type,p.cited_category,p.cited_filing_date)
        with ordinality as t(cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date,num)
    left outer join patents_global as p2 on (p2.publication_number_full=t.cited_publication_number_full OR p2.application_number_full=t.cited_application_number_full)
);

drop table big_query_citations_by_pub;
create table big_query_citations_by_pub (
    publication_number_full varchar(32) primary key,
    cited_publication_number_full varchar(32)[] not null,
    cited_publication_number_with_country varchar(32)[] not null,
    cited_publication_number varchar(32)[] not null,
    cited_application_number_formatted_with_country varchar(32)[] not null,
    cited_application_number_formatted varchar(32)[] not null,
    cited_npl_text text[] not null,
    cited_type varchar(32)[] not null,
    cited_category varchar(32)[] not null,
    cited_family_id varchar(32)[] not null,
    cited_filing_date date[] not null,
);

insert into big_query_citations_by_pub (
    select publication_number_full,
    array_agg(cited_application_number_full),
    array_agg(cited_publication_number_with_country),
    array_agg(cited_publication_number),
    array_agg(cited_application_number_formatted_with_country),
    array_agg(cited_application_number_formatted),
    array_agg(cited_npl_text),
    array_agg(cited_type),
    array_agg(cited_category),
    array_agg(cited_family_id),
    array_agg(cited_filing_date)
    from big_query_citations_helper
    group by publication_number_full
);


drop table big_query_reverse_citations;
create table big_query_reverse_citations (
    doc_number_full varchar(32) not null,
    is_filing boolean not null,
    rcite_publication_number_full varchar(32)[] not null,
    rcite_publication_number_with_country varchar(32)[] not null,
    rcite_publication_number varchar(32)[] not null,
    rcite_application_number_formatted_with_country varchar(32)[] not null,
    rcite_application_number_formatted varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_filing_date date[] not null,
    rcite_type varchar(32)[] not null,
    rcite_category varchar(32)[] not null,
    primary key (doc_number_full,is_filing)
);


insert into big_query_reverse_citations (
    select
        coalesce(temp.cited_publication_number_full,temp.cited_application_number_full),
        temp.cited_publication_number_full is null,
        (array_agg(publication_number_full))[1:10000], -- limit rcites
        (array_agg(country_code||publication_number))[1:10000], -- limit rcites
        (array_agg(publication_number))[1:10000], -- limit rcites
        (array_agg(country_code||application_number_formatted))[1:10000],
        (array_agg(application_number_formatted))[1:10000],
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
    rcite_publication_number_with_country varchar(32)[] not null,
    rcite_publication_number varchar(32)[] not null,
    rcite_application_number_formatted_with_country varchar(32)[] not null,
    rcite_application_number_formatted varchar(32)[] not null,
    rcite_family_id varchar(32)[] not null,
    rcite_filing_date date[] not null,
    rcite_type varchar(32)[] not null,
    rcite_category varchar(32)[] not null
);


insert into big_query_reverse_citations_by_pub (
    select publication_number_full,
    c.rcite_publication_number_full,
    c.rcite_publication_number_with_country,
    c.rcite_publication_number,
    c.rcite_application_number_formatted_with_country,
    c.rcite_application_number_formatted,
    c.rcite_family_id,
    c.rcite_filing_date,
    c.rcite_type,
    c.rcite_category
    from big_query_reverse_citations as c
    inner join patents_global as p on ((p.publication_number_full=c.doc_number_full and not c.is_filing) OR (p.application_number_full=c.doc_number_full and c.is_filing))
);


