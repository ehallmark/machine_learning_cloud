\connect patentdb

drop table big_query_embedding_by_pub;
create table big_query_embedding_by_pub (
    publication_number_full varchar(32) primary key,
    enc float[] not null
);

insert into big_query_embedding_by_pub (publication_number_full,enc) (
    select f.publication_number_full, p.enc from big_query_embedding_by_fam as p
    join big_query_family_id as f on (p.family_id=f.family_id)
    where p.family_id != '-1'
);