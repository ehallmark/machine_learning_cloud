\connect patentdb

create table big_query_ai_value_family_size (
    family_id varchar(32) primary key,
    family_size integer not null
);

insert into big_query_ai_value_family_size (family_id,family_size) (
    select family_id,count(*) from patents_global where family_id!='-1' group by family_id
);

create table big_query_ai_value_claims (
    family_id varchar(32) primary key,
    publication_number_full varchar(32) not null,
    means_present integer,
    num_claims integer,
    length_smallest_ind_claim integer
);

create table big_query_ai_value_assignments (
    family_id varchar(32) primary key,
    num_assignments integer not null
);

insert into big_query_ai_value_assignments (family_id,num_assignments) (
    select family_id,count(distinct reel_frame) from
        big_query_assignments left join big_query_assignment_documentid using (reel_frame)
    left outer join patents_global on
        (
            (
                (patents_global.publication_number=doc_number and not is_filing)
                OR
                (patents_global.application_number=doc_number and is_filing)
            ) AND patents_global.country_code='US'
        )
    where family_id!='-1' and patents_global.country_code='US' -- update -> and not family_id in (select family_id from big_query_ai_value_assignments)
    group by family_id
);

create table big_query_ai_value_citations (
    family_id varchar(32) primary key,
    num_rcites integer not null
);

insert into big_query_ai_value_citations (
    select family_id,count(distinct rcited_family_id) from (select
        doc_number_full,is_filing,t2.rcited_family_id from big_query_reverse_citations as t,unnest(t.rcite_family_id) with ordinality as t2(rcited_family_id,n)
    ) temp left outer join patents_global on
    (

        (patents_global.publication_number_full=doc_number_full and not is_filing)
        OR
        (patents_global.application_number_full=doc_number_full and is_filing)

    )
    where family_id !='-1' and not family_id in (select family_id from big_query_ai_value_citations)
    group by family_id
);

-- helper function to compute sigmoid
create or replace function sigmoid(double precision) returns double precision
    as 'select 1.0/(1.0+exp(0.0-$1));'
    language SQL
    immutable
    returns null on null input;

-- helper function to compute regression
create or replace function predict(double precision[], double precision[], double precision) returns double precision
    as 'select sigmoid(sum(x*w)+$3) from unnest($1,$2) with ordinality as t(x,w,n);'
    language SQL
    immutable
    returns null on null input;


create table big_query_ai_value (
    family_id varchar(32) primary key, -- eg. US9923222B1
    value double precision not null
);

create table big_query_ai_value_weights (
    date date primary key,
    weights double precision[] not null,
    intercept double precision not null
);


-- need to deal with missing values
insert into big_query_ai_value (family_id,values) (
    with avg_means_present as (
        select sum(means_present)::double precision/count(means_present) from big_query_ai_value_claims where means_present is not null
    ),
    avg_length_smallest_ind_claim as (
        select sum(length_smallest_ind_claim)::double precision/count(length_smallest_ind_claim) from big_query_ai_value_claims where length_smallest_ind_claim is not null
    ),
    avg_num_claims as (
        select sum(means_present)::double precision/count(num_claims) from big_query_ai_value_claims where num_claims is not null
    ),
    avg_num_assignments as (
        select sum(num_assignments)::double precision/count(num_assignments) from big_query_ai_value_assignments where num_assignments is not null
    ),
    avg_num_rcites as (
        select sum(num_rcites)::double precision/count(num_rcites) from big_query_ai_value_citations where num_rcites is not null
    ),
    avg_family_size as (
        select sum(family_size)::double precision/count(family_size) from big_query_ai_value_family_size where family_size is not null
    ),
    weights as (
        array((select weights from big_query_ai_value_weights order by date desc nulls last limit 1))[1]
    ), intercept as (
        array((select intercept from big_query_ai_value_weights order by date desc nulls last limit 1))[1]
    )
    select family_id,predict((
        Array[
            coalesce(means_present,avg_means_present),
            coalesce(num_claims,avg_num_claims),
            coalesce(num_assignments,avg_num_assignments),
            coalesce(family_size,avg_family_size),
            coalesce(length_smallest_ind_claim,avg_length_smallest_ind_claim),
            coalesce(num_rcites,avg_num_rcites)
         ]
    )::double precision[],weights,intercept) as value
    from big_query_ai_value_assignments
    left outer join big_query_ai_value_citations using (family_id)
    left outer join big_query_ai_value_claims using (family_id)
    left outer join big_query_ai_value_family_size using(family_id)
);
