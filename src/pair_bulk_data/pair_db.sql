\connect patentdb

CREATE TABLE pair_applications (
    application_number text PRIMARY KEY,
    filing_date date,
    application_type text,
    correspondence_address_id text,
    group_art_unit_number text,
    entity_type text,
    application_confirmation_number text,
    invention_title text,
    application_status text,
    application_status_date date,
    grant_number text,
    grant_date date,
    publication_number text,
    publication_date date,
    term_extension int not null default 0,
    applicant_file_reference text
);

ALTER TABLE pair_applications ADD COLUMN assignee text;

CREATE TABLE pair_application_inventors (
    first_name text,
    last_name text,
    city text,
    country text,
    application_number text references pair_applications(application_number)
);


select count(distinct application_number) from
    (select a.application_number, i.first_name,i.last_name,i.city,i.country from pair_applications as a
        join pair_application_inventors as i on (a.application_number=i.application_number)
        where first_name is not null and last_name is not null and city is not null and country is not null and filing_date is not null
        and date_part('year', filing_date) = 2017
    ) as temp;