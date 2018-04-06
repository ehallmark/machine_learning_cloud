
\connect patentdb

create table big_query_patents (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    publication_number varchar(32) not null, -- eg. 9923222
    application_number_full varchar(32),
    application_number varchar(32), -- eg. 20012322342
    application_number_formatted varchar(32), -- eg. 12002328
    filing_date date not null,
    publication_date date,
    priority_date date not null,
    country_code varchar(8),
    kind_code varchar(8),
    application_kind varchar(8),
    family_id varchar(32) not null,
    ai_value double precision,
    latest_execution_date date,
    term_adjustments integer,
    original_entity_type varchar(32),
    means_present boolean,
    length_of_smallest_independent_claim integer,
    wipo_technology text,
    gtt_technology text[],
    expired boolean,
    lapsed boolean,
    reinstated boolean,
    priority_date_calculated date,
    expiration_date_calculated date,
    granted boolean
);
create index big_query_patent_family_application_number_formatted_idx on big_query_patents (application_number_formatted);
create index big_query_patent_family_family_id_idx on big_query_patents (family_id);

