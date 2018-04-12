
\connect patentdb

create table patents_global_es (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    publication_number varchar(32) not null, -- eg. 9923222
    publication_number_full varchar(32) not null,
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
    ai_value double precision,
    length_of_smallest_ind_claim integer,
    means_present integer,
    -- sep
    sso text[],
    standard text[],
    -- wipo
    wipo_technology text,
    -- gtt tech
    technology text[],
    -- maintenance events
    maintenance_event text[],
    lapsed boolean,
    reinstated boolean,
    latest_assignee text[],
    latest_assignee_date date,
    security_interest boolean,
    -- embedding
    encoding float[],
    -- assignments
    reel_frame text[],
    conveyance_text text[],
    execution_date date[],
    recorded_date date[],
    first_assignee text[], -- first assignee of each reel frame
    first_assignor text[], -- first assignor of each reel frame
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
    -- reverse citations
    rcite_publication_number_full varchar(32)[],
    rcite_application_number_full varchar(32)[],
    rcite_family_id varchar(32)[],
    rcite_filing_date date[],
    -- pair (incorporate 'abandoned' field into 'lapsed')
    term_adjustments integer

);


insert into patents_global_es (
        publication_number_full, -- eg. US9923222B1
        publication_number, -- eg. 9923222
        publication_number_with_country,
        application_number_full,
        application_number, -- eg. 20012322342
        application_number_with_country,
        application_number_formatted, -- eg. 12002328
        application_number_formatted_with_country,
        filing_date,
        publication_date,
        priority_date,
        country_code,
        kind_code,
        application_kind,
        family_id,
        original_entity_type,
        invention_title,
        abstract,
        claims,
        description,
        inventor,
        assignee,
        inventor_harmonized,
        inventor_harmonized_cc,
        assignee_harmonized,
        assignee_harmonized_cc,
        ai_value,
        length_of_smallest_ind_claim,
        means_present,
        -- sep
        sso text[],
        standard text[],
        -- wipo
        wipo_technology text,
        -- gtt tech
        technology text[],
        -- maintenance events
        maintenance_event text[],
        lapsed boolean,
        reinstated,
        latest_assignee,
        latest_assignee_date,
        security_interest,
        -- embedding
        encoding,
        -- assignments
        reel_frame,
        conveyance_text,
        execution_date,
        recorded_date,
        first_assignee, -- first assignee of each reel frame
        first_assignor, -- first assignor of each reel frame
        -- priority claims
        pc_publication_number_full,
        pc_application_number_full,
        pc_filing_date,
        -- cpc
        code,
        tree,
        inventive,
        -- citations
        cited_publication_number_full,
        cited_application_number_full,
        cited_npl_text,
        cited_type,
        cited_category,
        cited_filing_date,
        -- reverse citations
        rcite_publication_number_full,
        rcite_application_number_full],
        rcite_family_id,
        rcite_filing_date,
        -- pair (incorporate 'abandoned' field into 'lapsed')
        term_adjustments
)
(
    select   -- monster query
);
