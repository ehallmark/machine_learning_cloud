\connect patentdb

create table big_query_test (
    -- copied from patents_global_merged
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    publication_number varchar(32) not null, -- eg. 9923222
    publication_number_with_country varchar(32) not null,
    application_number_full varchar(32),
    application_number varchar(32), -- eg. 20012322342
    application_number_with_country varchar(32),
    application_number_formatted varchar(32), -- eg. 12002328
    application_number_formatted_with_country varchar(32),
    filing_date date not null,
    publication_date date,
    priority_date date not null,
    country_code varchar(8),
    kind_code varchar(8),
    application_kind varchar(8),
    family_id varchar(32) not null,
    original_entity_type varchar(32),
    invention_title text,
    abstract text,
    claims text,
    description text,
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
    tree varchar(32)[],
    inventive boolean[],
    -- citations
    cited_publication_number_full varchar(32)[],
    cited_application_number_full varchar(32)[],
    cited_npl_text text[],
    cited_type varchar(32)[],
    cited_category varchar(32)[],
    cited_filing_date date[],
    -- value
    ai_value double precision,
    length_of_smallest_ind_claim integer,
    means_present integer,
    family_size integer,
    -- sep
    sso text[],
    standard text[],
    -- wipo
    wipo_technology text,
    -- gtt tech
    technology text,
    technology2 text,
    -- maintenance events
    maintenance_event text[],
    lapsed boolean,
    reinstated boolean,

    latest_assignee text[],
    latest_assignee_date date,
    latest_security_interest boolean,
    latest_first_assignee text,
    latest_portfolio_size integer,
    latest_entity_type varchar(32),
    latest_first_filing_date date,
    latest_last_filing_date date,

    -- latest assignee fam
    latest_fam_assignee text[],
    latest_fam_assignee_date date,
    latest_fam_security_interest boolean,
    latest_fam_first_assignee text,
    latest_fam_portfolio_size integer,
    latest_fam_entity_type varchar(32),
    latest_fam_first_filing_date date,
    latest_fam_last_filing_date date,

    -- embedding
    cpc_vae float[],
    rnn_enc float[],
    -- assignments
    reel_frame text[],
    conveyance_text text[],
    execution_date date[],
    recorded_date date[],
    recorded_assignee text[][], -- first assignee of each reel frame
    recorded_assignor text[][], -- first assignor of each reel frame
    -- reverse citations
    rcite_publication_number_full varchar(32)[],
    rcite_application_number_full varchar(32)[],
    rcite_family_id varchar(32)[],
    rcite_filing_date date[],
    -- pair (incorporate 'abandoned' field into 'lapsed')
    term_adjustments integer,
    compdb_deal_id varchar(32)[],
    compdb_recorded_date date[],
    compdb_technology text[],
    compdb_inactive boolean[],
    compdb_acquisition boolean[],

    gather_value integer,
    gather_stage varchar(32)[],
    gather_technology text[],
    ptab_appeal_no varchar(100)[],
    ptab_interference_no varchar(100)[],
    ptab_mailed_date date[],
    ptab_inventor_last_name text[],
    ptab_inventor_first_name text[],
    ptab_case_name text[],
    ptab_case_type varchar(100)[],
    ptab_case_status varchar(50)[],
    ptab_case_text text[]
);


insert into big_query_test (
    select * from patents_global_merged tablesample system (0.083)
);

pg_dump -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb -t big_query_test > data/big_query_test.dump
