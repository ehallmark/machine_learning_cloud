\connect patentdb

drop table big_query_patent_english_claims;
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
    where claims[array_position(claims_lang,'en')] is not null and family_id != '-1'
    order by family_id,case when country_code='US' then 1 else 0 end desc,publication_date desc nulls last,filing_date desc nulls last
);

create table big_query_patent_english_abstract (
    family_id varchar(32) primary key,
    publication_number_full varchar(32) not null,
    abstract text not null
);
create index big_query_patent_english_abstract_pub_idx on big_query_patent_english_abstract (publication_number_full);

insert into big_query_patent_english_abstract (family_id,publication_number_full,abstract) (
    select distinct on (family_id)
        family_id,publication_number_full,
        abstract[array_position(abstract_lang,'en')]
    from patents_global
    where abstract[array_position(abstract_lang,'en')] is not null and family_id != '-1'
    order by family_id,case when country_code='US' then 1 else 0 end desc,publication_date desc nulls last,filing_date desc nulls last
);

create table big_query_patent_english_description (
    family_id varchar(32) primary key,
    publication_number_full varchar(32) not null,
    description text not null
);

insert into big_query_patent_english_description (family_id,publication_number_full,description) (
    select distinct on (family_id)
        family_id,publication_number_full,
        description[array_position(description_lang,'en')]
    from patents_global
    where description[array_position(description_lang,'en')] is not null and family_id != '-1'
    order by family_id,case when country_code='US' then 1 else 0 end desc,publication_date desc nulls last,filing_date desc nulls last
);