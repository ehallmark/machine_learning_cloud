
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
    third_party boolean not null default('f'),
    reciprocity boolean not null default('f');
    publication_number
);

create index big_query_sep_family_id on big_query_sep (family_id);