
\connect patentdb

create table patents_global_merged (
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
    claim_count integer,
    description text,
    inventor text[],
    assignee text[],
    inventor_harmonized text[],
    inventor_harmonized_cc varchar(8)[],
    inventor_count integer,
    assignee_harmonized text[],
    assignee_harmonized_cc varchar(8)[],
    assignee_count integer,
    -- priority claims
    pc_publication_number_full varchar(32)[],
    pc_application_number_full varchar(32)[],
    pc_filing_date date[],
    pc_count integer,
    -- cpc
    code varchar(32)[],
    code_count integer,
    tree varchar(32)[],
    inventive boolean[],
    -- citations
    cited_publication_number_full varchar(32)[],
    cited_application_number_full varchar(32)[],
    cited_npl_text text[],
    cited_type varchar(32)[],
    cited_category varchar(32)[],
    cited_filing_date date[],
    citation_count integer,

    -- value
    ai_value double precision,
    length_of_smallest_ind_claim integer,
    means_present integer,
    family_size integer,
    -- sep
    sso text[],
    standard text[],
    standard_count integer,

    -- wipo
    wipo_technology text,
    -- gtt tech
    technology text,
    technology2 text,
    keywords text[],
    keyword_count integer,
    -- maintenance events
    maintenance_event text[],
    maintenance_event_count integer,
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
    latest_assignee_count integer,

    -- latest assignee fam
    latest_fam_assignee text[],
    latest_fam_assignee_date date,
    latest_fam_security_interest boolean,
    latest_fam_first_assignee text,
    latest_fam_portfolio_size integer,
    latest_fam_entity_type varchar(32),
    latest_fam_first_filing_date date,
    latest_fam_last_filing_date date,
    latest_fam_assignee_count integer,

    -- embedding
    cpc_vae float[],
    rnn_enc float[],
    -- assignments
    reel_frame text[],
    assignment_count integer,
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
    rcite_type varchar(32)[],
    rcite_category varchar(32)[],
    rcite_count integer,
    -- pair (incorporate 'abandoned' field into 'lapsed')
    term_adjustments integer,
    compdb_deal_id varchar(32)[],
    compdb_recorded_date date[],
    compdb_technology text[],
    compdb_inactive boolean[],
    compdb_acquisition boolean[],
    compdb_count integer,

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
    ptab_case_text text[],
    ptab_count integer
);


insert into patents_global_merged (
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
        claim_count,
        description,
        inventor,
        assignee,
        inventor_harmonized,
        inventor_harmonized_cc,
        inventor_count,
        assignee_harmonized,
        assignee_harmonized_cc,
        assignee_count,
        -- priority claims
        pc_publication_number_full,
        pc_application_number_full,
        pc_filing_date,
        pc_count,
        -- cpc
        code,
        code_count,
        tree,
        inventive,
        -- citations
        cited_publication_number_full,
        cited_application_number_full,
        cited_npl_text,
        cited_type,
        cited_category,
        cited_filing_date,
        citation_count,
        -- value
        ai_value,
        length_of_smallest_ind_claim,
        means_present,
        family_size,
        -- sep
        sso,
        standard,
        standard_count,
        -- wipo
        wipo_technology,
        -- gtt tech
        technology,
        technology2,
        keywords,
        keyword_count,
        -- maintenance events
        maintenance_event,
        maintenance_event_count,
        lapsed,
        reinstated,
        -- latest assignee pub
        latest_assignee,
        latest_assignee_date,
        latest_security_interest,
        latest_first_assignee,
        latest_portfolio_size,
        latest_entity_type,
        latest_first_filing_date,
        latest_last_filing_date,
        latest_assignee_count,
        -- latest assignee fam
        latest_fam_assignee,
        latest_fam_assignee_date,
        latest_fam_security_interest,
        latest_fam_first_assignee,
        latest_fam_portfolio_size,
        latest_fam_entity_type,
        latest_fam_first_filing_date,
        latest_fam_last_filing_date,
        latest_fam_assignee_count,
        -- embedding
        cpc_vae,
        rnn_enc,
        -- assignments
        reel_frame,
        conveyance_text,
        execution_date,
        recorded_date,
        recorded_assignee, -- first assignee of each reel frame
        recorded_assignor, -- first assignor of each reel frame
        assignment_count,
        -- reverse citations
        rcite_publication_number_full,
        rcite_application_number_full,
        rcite_family_id,
        rcite_filing_date,
        rcite_type,
        rcite_category,
        rcite_count,
        -- pair (incorporate 'abandoned' field into 'lapsed')
        term_adjustments,
        -- compdb
        compdb_deal_id,
        compdb_recorded_date,
        compdb_technology,
        compdb_inactive,
        compdb_acquisition,
        compdb_count,
        -- gather
        gather_value,
        gather_stage,
        gather_technology,
        ptab_appeal_no,
        ptab_interference_no,
        ptab_mailed_date,
        ptab_inventor_last_name,
        ptab_inventor_first_name,
        ptab_case_name,
        ptab_case_type,
        ptab_case_status,
        ptab_case_text,
        ptab_count
)
(
    select   -- monster query
        p.publication_number_full,
        p.publication_number,
        p.country_code||p.publication_number,
        p.application_number_full,
        p.application_number,
        p.country_code||p.application_number,
        p.application_number_formatted,
        p.country_code||p.application_number_formatted,
        p.filing_date,
        p.publication_date,
        p.priority_date,
        p.country_code,
        p.kind_code,
        p.application_kind,
        p.family_id,
        coalesce(m.original_entity_status,pair.original_entity_type),
        p.invention_title[array_position(p.invention_title_lang,'en')],
        p.abstract[array_position(p.abstract_lang,'en')],
        p.claims[array_position(p.claims_lang,'en')],
        value_claims.num_claims,
        p.description[array_position(p.description_lang,'en')],
        p.inventor,
        p.assignee,
        p.inventor_harmonized,
        p.inventor_harmonized_cc,
        coalesce(array_length(p.inventor_harmonized,1),0),
        p.assignee_harmonized,
        p.assignee_harmonized_cc,
        coalesce(array_length(p.assignee_harmonized,1),0),
        -- priority claims
        p.pc_publication_number_full,
        p.pc_application_number_full,
        p.pc_filing_date,
        coalesce(array_length(p.pc_publication_number_full,1),0),
        -- cpc
        p.code,
        coalesce(array_length(p.code,1),0),
        cpc_tree.tree,
        p.inventive,
        -- citations
        p.cited_publication_number_full,
        p.cited_application_number_full,
        p.cited_npl_text,
        p.cited_type,
        p.cited_category,
        p.cited_filing_date,
        coalesce(array_length(p.cited_publication_number_full,1),0),
        -- ai value
        ai_value.value,
        -- other value attrs
        value_claims.length_smallest_ind_claim,
        value_claims.means_present,
        value_family_size.family_size,
        -- sep
        sep.sso,
        sep.standard,
        coalesce(array_length(sep.standard,1),0),
        -- wipo tech
        wipo.wipo_technology,
        -- gtt tech
        tech.technology,
        tech.secondary,
        ke.keywords,
        coalesce(array_length(ke.keywords,1),0),
        -- maintenance events
        m_codes.codes,
        coalesce(array_length(m_codes.codes,1),0),
        coalesce(coalesce(m.lapsed,pair.abandoned),'f'),
        coalesce(m.reinstated,'f'),
        -- latest assignee by pub
        latest_assignee.assignee,
        latest_assignee.date,
        latest_assignee.security_interest,
        latest_assignee.first_assignee,
        latest_assignee_join.portfolio_size,
        latest_assignee_join.entity_type,
        latest_assignee_join.first_filing_date,
        latest_assignee_join.last_filing_date,
        coalesce(array_length(latest_assignee.assignee,1),0),
        -- latest assignee by fam
        latest_assignee_fam.assignee,
        latest_assignee_fam.date,
        latest_assignee_fam.security_interest,
        latest_assignee_fam.first_assignee,
        latest_assignee_fam_join.portfolio_size,
        latest_assignee_fam_join.entity_type,
        latest_assignee_fam_join.first_filing_date,
        latest_assignee_fam_join.last_filing_date,
        coalesce(array_length(latest_assignee_fam.assignee,1),0),
        -- embedding
        enc1.cpc_vae,
        enc2.rnn_enc,
        -- assignments
        a.reel_frame,
        a.conveyance_text,
        a.execution_date,
        a.recorded_date,
        a.assignee, -- first assignee of each reel frame
        a.assignor, -- first assignor of each reel frame
        coalesce(array_length(a.reel_frame,1),0),
        -- rcites
        rc.rcite_publication_number_full,
        rc.rcite_application_number_full,
        rc.rcite_family_id,
        rc.rcite_filing_date,
        rc.rcite_type,
        rc.rcite_category,
        coalesce(array_length(rc.rcite_publication_number_full,1),0),
        -- pair
        pair.term_adjustments,
        -- compdb
        compdb.deal_id,
        compdb.recorded_date,
        compdb.technology,
        compdb.inactive,
        compdb.acquisition,
        array_length(compdb.deal_id,1),
        -- gather
        gather.value,
        gather.stage,
        gather.technology,
        ptab.appeal_no,
        ptab.interference_no,
        ptab.mailed_date,
        ptab.inventor_last_name,
        ptab.inventor_first_name,
        ptab.case_name,
        ptab.doc_type,
        ptab.status,
        ptab.doc_text,
        coalesce(array_length(ptab.appeal_no,1),0)


    from patents_global as p
    left outer join big_query_pair_by_pub as pair on (p.publication_number_full=pair.publication_number_full)
    left outer join big_query_maintenance_by_pub as m on (m.publication_number_full=p.publication_number_full)
    left outer join big_query_maintenance_codes_by_pub as m_codes on (m_codes.publication_number_full=p.publication_number_full)
    left outer join big_query_reverse_citations_by_pub as rc on (rc.publication_number_full=p.publication_number_full)
    left outer join big_query_patent_to_latest_assignee_by_pub as latest_assignee on (latest_assignee.publication_number_full=p.publication_number_full)
        left outer join big_query_assignee as latest_assignee_join on (latest_assignee_join.name=latest_assignee.first_assignee)
    left outer join big_query_patent_to_latest_assignee_by_family as latest_assignee_fam on (latest_assignee_fam.family_id=p.family_id)
        left outer join big_query_assignee as latest_assignee_fam_join on (latest_assignee_fam.first_assignee=latest_assignee_fam_join.name)
    left outer join big_query_technologies2 as tech on (p.family_id=tech.family_id)
    left outer join big_query_keywords_all as ke on (p.family_id=ke.family_id)
    left outer join big_query_sep_by_family as sep on (sep.family_id=p.family_id)
    left outer join big_query_wipo_by_family as wipo on (wipo.family_id=p.family_id)
    left outer join big_query_ai_value_claims as value_claims on (value_claims.family_id=p.family_id)
    left outer join big_query_ai_value_family_size as value_family_size on (value_family_size.family_id=p.family_id)
    left outer join big_query_ai_value as ai_value on (ai_value.family_id=p.family_id)
    left outer join big_query_embedding1 as enc1 on (enc1.family_id=p.family_id)
    left outer join big_query_embedding2 as enc2 on (enc2.family_id=p.family_id)
    left outer join big_query_cpc_tree as cpc_tree on (cpc_tree.publication_number_full=p.publication_number_full)
    left outer join big_query_gather_with_pub as gather on (gather.publication_number_full=p.publication_number_full)
    left outer join big_query_compdb_deals_by_pub as compdb on (compdb.publication_number_full=p.publication_number_full)
    left outer join big_query_assignment_documentid_by_pub as a on (p.publication_number_full=a.publication_number_full)
    left outer join big_query_ptab_by_pub as ptab on (p.publication_number_full=ptab.publication_number_full)

);

vacuum;

-- dump all
pg_dump -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb > /usb2/big_query_database.dump
