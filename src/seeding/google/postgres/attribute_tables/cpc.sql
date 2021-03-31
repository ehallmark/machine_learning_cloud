\connect patentdb

drop table big_query_cpc;
create table big_query_cpc (
    publication_number_full varchar(32) not null,
    code text not null,
    primary key (publication_number_full, code)
);