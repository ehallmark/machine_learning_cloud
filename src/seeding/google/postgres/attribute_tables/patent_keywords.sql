\connect patentdb

create table big_query_keywords_all (
    family_id varchar(32) primary key,
    keywords text[] not null
);

create table big_query_keyword_count_helper (
    keyword text primary key,
    num_words integer not null,
    doc_count integer not null
);

insert into big_query_keyword_count_helper (keyword,num_words,doc_count) (
    select w.keyword,
        1+char_length(w.keyword)-char_length(replace(w.keyword,' ','')) as num_words,
        count(distinct family_id) as doc_count
    from big_query_keywords_all as b, unnest(b.keywords) as w(keyword)
    group by w.keyword
    having count(distinct family_id) >= 30 -- prune really rare keywords
);

-- build tf-idf vectors
create table big_query_keywords_tfidf (
    family_id varchar(32) primary key,
    keywords text[] not null
);

