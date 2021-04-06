\connect patentdb

drop table big_query_cpc_grouped;
create table big_query_cpc_grouped (
    publication_number_full varchar(32) primary key,
    code text[] not null
);

insert into big_query_cpc_grouped (
    select publication_number_full, array_agg(code) from big_query_cpc group by publication_number_full
);