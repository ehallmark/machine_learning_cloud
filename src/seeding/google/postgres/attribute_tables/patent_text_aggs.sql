\connect patentdb


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