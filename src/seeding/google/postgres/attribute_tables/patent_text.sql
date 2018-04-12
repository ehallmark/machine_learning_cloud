\connect patentdb

create table big_query_patent_english_claims (
    family_id varchar(32) primary key,
    publication_number_full varchar(32) not null,
    abstract text,
    claims text not null
);

insert into big_query_patent_english_claims (family_id,publication_number_full,abstract,claims) (
    select distinct on (family_id)
        family_id,publication_number_full,
        abstract[array_position(abstract_lang,'en')],
        claims[array_position(claims_lang,'en')]
    from patents_global
    where claims[array_position(claims_lang,'en')] is not null
    order by family_id,publication_date desc nulls last,filing_date desc nulls last
);

create table big_query_patent_english_abstract (
    family_id varchar(32) primary key,
    publication_number_full varchar(32) not null,
    abstract text not null
);

insert into big_query_patent_english_abstract (family_id,publication_number_full,abstract) (
    select distinct on (family_id)
        family_id,publication_number_full,
        abstract[array_position(abstract_lang,'en')]
    from patents_global
    where abstract[array_position(abstract_lang,'en')] is not null
    order by family_id,publication_date desc nulls last,filing_date desc nulls last
);