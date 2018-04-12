\connect patentdb

create table big_query_wipo (
    publication_number varchar(32) primary key,
    wipo_technology text not null
);

create table big_query_wipo_family_id (
    publication_number varchar(32) primary key,
    family_id varchar(32) not null
);

insert into big_query_wipo_family_id (publication_number,family_id) (
    select
);

create index big_query_wipo_family_id_idx on big_query_wipo_family_id (publication_number,family_id);