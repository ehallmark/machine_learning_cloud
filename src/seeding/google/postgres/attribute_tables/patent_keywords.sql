\connect patentdb

drop table big_query_keywords_all;
create table big_query_keywords_all (
    family_id varchar(32) primary key,
    keywords text[] not null
);


-- build tf-idf vectors
drop table big_query_keywords_tfidf;
create table big_query_keywords_tfidf (
    family_id varchar(32) primary key,
    keywords text[] not null
);

