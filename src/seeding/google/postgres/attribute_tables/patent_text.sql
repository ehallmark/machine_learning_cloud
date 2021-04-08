\connect patentdb

drop table big_query_patent_english_claims;
create table big_query_patent_english_claims (
    publication_number_full varchar(32) primary key,
    abstract text,
    claims text not null,
    means_present boolean,
    num_claims integer,
    length_smallest_ind_claim integer
);