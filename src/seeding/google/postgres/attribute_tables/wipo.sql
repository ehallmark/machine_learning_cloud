\connect patentdb

create table big_query_wipo (
    publication_number varchar(32) primary key,
    wipo_technology text not null
);
