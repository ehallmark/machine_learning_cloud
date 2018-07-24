\connect patentdb


insert into big_query_patent_english_abstract (family_id,publication_number_full,abstract) (
    select distinct on (p.family_id)
        p.family_id,p.publication_number_full,
        p.abstract[array_position(p.abstract_lang,'en')]
    from patents_global as p full outer join big_query_patent_english_abstract as a on (a.family_id=p.family_id)
    where p.abstract[array_position(p.abstract_lang,'en')] is not null and p.family_id != '-1' and a.family_id is null
    order by p.family_id,case when p.country_code='US' then 1 else 0 end desc,p.publication_date desc nulls last,p.filing_date desc nulls last
);


insert into big_query_patent_english_description (family_id,publication_number_full,description) (
    select distinct on (p.family_id)
        p.family_id,p.publication_number_full,
        p.description[array_position(p.description_lang,'en')]
    from patents_global as p full outer join big_query_patent_english_description as d on (d.family_id=p.family_id)
    where p.description[array_position(description_lang,'en')] is not null and p.family_id != '-1' and d.family_id is null
    order by p.family_id,case when p.country_code='US' then 1 else 0 end desc,p.publication_date desc nulls last,p.filing_date desc nulls last
);


-- update patent claim text
insert into big_query_patent_english_claims (
    select p.publication_number_full,
        p.abstract[array_position(p.abstract_lang,'en')],
        p.claims[array_position(p.claims_lang,'en')],
        p.means_present,
        p.num_claims,
        p.length_of_smallest_ind_claim
    from patents_global as p full outer join big_query_patent_english_claims as d on (d.publication_number_full=p.publication_number_full)
    where p.claims[array_position(p.claims_lang,'en')] is not null and d.publication_number_full is null
);