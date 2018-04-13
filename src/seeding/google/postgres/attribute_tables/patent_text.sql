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
    where claims[array_position(claims_lang,'en')] is not null and family_id != '-1'
    order by family_id,publication_date desc nulls last,filing_date desc nulls last
);

-- then redo claim value metrics
insert into big_query_ai_value_claims (family_id,means_present,num_claims,length_smallest_ind_claim) (
    select family_id,
    case when bool_and(case when claim ~ 'claim [0-9]' then null else (claim like '% means %')::boolean end) then 1 else 0 end,
    count(*) as num_claims,
    min(case when claim ~ 'claim [0-9]' then null else array_length(array_remove(regexp_split_to_array(claim,'\\s+'),''),1) end)
    from big_query_patent_english_claims as p, unnest(regexp_split_to_array(claims,'(\\s*\\n\\s*[0-9]*\\s*\\.)')) with ordinality as c(claim,n)
    where claim is not null and trim(claim)!=''
    group by family_id
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
    where abstract[array_position(abstract_lang,'en')] is not null and family_id != '-1'
    order by family_id,publication_date desc nulls last,filing_date desc nulls last
);