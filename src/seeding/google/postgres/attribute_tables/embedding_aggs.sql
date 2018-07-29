\connect patentdb


insert into big_query_embedding_by_pub (publication_number_full,enc) (
    select publication_number_full, enc from big_query_embedding_by_fam as p
    join big_query_family_id as f on (p.family_id=f.family_id)
    where p.family_id != '-1'
);