\connect patentdb

drop table big_query_wipo;
create table big_query_wipo (
    publication_number varchar(32) not null,
    sequence integer not null,
    wipo_technology text not null,
    primary key (publication_number, sequence)
);


drop table big_query_wipo_prediction;
create table big_query_wipo_prediction (
    publication_number_full varchar(32) primary key,
    wipo_technology text not null
);
