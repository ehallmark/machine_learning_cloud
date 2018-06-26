\connect patentdb

drop table big_query_wipo;
create table big_query_wipo (
    publication_number varchar(32) not null,
    sequence integer not null,
    wipo_technology text not null,
    primary key (publication_number, sequence)
);
