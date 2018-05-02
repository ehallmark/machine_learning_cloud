\connect patentdb

create table big_query_keywords_all (
    family_id varchar(32) primary key,
    keywords text[] not null
);

create table big_query_keywords_tfidf (
    family_id varchar(32) primary key,
    keywords text[] not null
);