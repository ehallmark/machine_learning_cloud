\connect patentdb

delete from big_query_embedding_by_fam where family_id in (
    select distinct f.family_id from big_query_family_id as f
    join big_query_embedding_by_fam as e on (e.family_id=f.family_id)
    where e.family_id!='-1' and (kind_code like 'S%' or kind_code like 'P%')
);


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