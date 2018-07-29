\connect patentdb


insert into big_query_embedding_by_pub (publication_number_full,enc) (
    select f.publication_number_full, p.enc from big_query_embedding_by_fam as p
    join big_query_family_id as f on (p.family_id=f.family_id)
    full outer join big_query_embedding_by_pub as e
    on (e.publication_number_full=f.publication_number_full)
    where p.family_id != '-1' and e.publication_number_full is null
);