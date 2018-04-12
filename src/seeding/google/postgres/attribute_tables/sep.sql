
\connect patentdb

create table big_query_sep (
    record_id varchar(32) primary key, -- eg. US9923222B1
    family_id varchar(32), -- null means no matched patents
    disclosure_event text,
    sso text,
    patent_owner_harmonized text,
    patent_owner_unharmonized text,
    date date,
    standard text,
    licensing_commitment text,
    blanket_type integer,
    blanket_scope text,
    third_party boolean,
    reciprocity boolean,
    publication_number_with_country text
);

create index big_query_sep_family_id on big_query_sep (family_id);

create table big_query_sep_by_family (
    family_id varchar(32) primary key,
    sso text[] not null,
    standard text[] not null,
    date date[] not null
);


insert into big_query_sep_by_family (family_id,sso,standard,date) (
    select family_id,array_agg(sso),array_agg(standard),array_agg(date) from big_query_sep
    where (sso is not null OR standard is not null) and family_id is not null and family_id !='-1'
    group by family_id
);