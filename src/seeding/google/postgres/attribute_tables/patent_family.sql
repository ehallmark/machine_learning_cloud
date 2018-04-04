
\connect patentdb

create table big_query_patent_to_patent_family (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    publication_number varchar(32) not null, -- eg. US9923222
    application_number_full varchar(32) not null,
    application_number varchar(32), -- eg. US20012322342
    filing_date date not null,
    publication_date date not null,
    priority_date date not null,
    country_code varchar(8),
    kind_code varchar(8),
    application_kind varchar(8),
    family_id varchar(32) not null,
    ai_value double,
    latest_execution_date date,
    term_adjustments integer not null default(0),
    original_entity_type varchar(32) not null,
    means_present boolean,
    length_of_smallest_independent_claim integer,
    wipo_technology text,
    gtt_technology text[],
    expired boolean,
    lapsed boolean,
    reinstated boolean not null default(false),
    priority_date_calculated date,
    expiration_date_calculated date,
    granted boolean
);
create index big_query_patent_family_publication_number_idx on big_query_patent_to_patent_family (publication_number);
create index big_query_patent_family_application_number_full_idx on big_query_patent_to_patent_family (application_number_full);
create index big_query_patent_family_family_id_idx on big_query_patent_to_patent_family (family_id);