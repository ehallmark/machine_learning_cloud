
\connect patentdb

create table patents_global (
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
    original_entity_type varchar(32),
    invention_title text[],
    invention_title_lang varchar(8)[],
    abstract text[],
    abstract_lang varchar(8)[],
    claims text[],
    claims_lang varchar(8)[],
    description text[],
    description_lang varchar(8)[],
    inventor text[],
    assignee text[],
    inventor_harmonized text[],
    inventor_harmonized_cc varchar(8)[],
    assignee_harmonized text[],
    assignee_harmonized_cc varchar(8)[],
    -- priority claims
    pc_publication_number_full varchar(32)[],
    pc_application_number_full varchar(32)[],
    pc_filing_date date[],
    -- cpc
    code varchar(32)[],
    inventive boolean[],
    -- citations
    cited_publication_number_full varchar(32)[],
    cited_application_number_full varchar(32)[],
    cited_npl_text text[],
    cited_type varchar(32)[],
    cited_category varchar(32)[],
    cited_filing_date date[],
);
create index patents_global_family_id_idx on patents_global (family_id);

