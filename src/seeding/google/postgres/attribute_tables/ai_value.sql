\connect patentdb

drop table big_query_ai_value_family_size;
create table big_query_ai_value_family_size (
    family_id varchar(32) primary key,
    family_size integer not null
);

insert into big_query_ai_value_family_size (family_id,family_size) (
    select family_id,count(*) from patents_global where family_id!='-1' group by family_id
);


drop table big_query_ai_value_claims;
create table big_query_ai_value_claims (
    family_id varchar(32) primary key,
    means_present integer,
    num_claims integer,
    length_smallest_ind_claim integer
); -- insert script is located in patent_text.sql


drop table big_query_ai_value_assignments;
create table big_query_ai_value_assignments (
    family_id varchar(32) primary key,
    num_assignments integer not null
);

insert into big_query_ai_value_assignments (family_id,num_assignments) (
    select family_id,count(distinct reel_frame) from
        big_query_assignments left join big_query_assignment_documentid using (reel_frame)
    left outer join patents_global on
        (
           patents_global.application_number_formatted=doc_number
        )
    where is_filing and patents_global.country_code='US' and family_id!='-1' and patents_global.country_code='US' -- update -> and not family_id in (select family_id from big_query_ai_value_assignments)
    group by family_id
);

drop table big_query_ai_value_citations;
create table big_query_ai_value_citations (
    family_id varchar(32) primary key,
    num_rcites integer not null
);

insert into big_query_ai_value_citations (
    select family_id,count(distinct rcited_family_id) from (select
        publication_number_full,t2.rcited_family_id from big_query_reverse_citations_by_pub as t,unnest(t.rcite_family_id) with ordinality as t2(rcited_family_id,n)
    ) temp left outer join patents_global on
        (patents_global.publication_number_full=temp.publication_number_full)
    where family_id !='-1' -- and not family_id in (select family_id from big_query_ai_value_citations)
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

create table big_query_ai_value_all (
    family_id varchar(32) primary key,
    means_present integer,
    num_claims integer,
    length_smallest_ind_claim integer,
    num_rcites integer,
    num_assignments integer,
    family_size integer
);

insert into big_query_ai_value_all (family_id,means_present,num_claims,length_smallest_ind_claim,num_rcites,num_assignments,family_size) (
    select
    family_id,
    means_present,
    num_claims,
    length_smallest_ind_claim,
    num_rcites,
    num_assignments,
    family_size
    from big_query_ai_value_family_size
    left outer join big_query_ai_value_citations using (family_id)
    left outer join big_query_ai_value_claims using (family_id)
    left outer join big_query_ai_value_assignments using(family_id)
);


create table big_query_ai_value_weights (
    date date primary key,
    weights double precision[] not null,
    intercept double precision not null
);

-- latest model
insert into big_query_ai_value_weights (date,weights,intercept) values (
    '2018-04-13'::date,
    (Array[-0.5319562, 0.0122155, 0.0136566, 0.0062181, -0.0056129, 0.0057363])::double precision[],
    -1.0491014::double precision
);

create table big_query_gather_wipo_stats_by_family_id (
    family_id varchar(32) primary key,
    score double precision not null
);

insert into big_query_gather_wipo_stats_by_family_id (family_id,score) (
    select family_id,(frequency)*(select max(frequency) as max from big_query_gather_wipo_stats) as score
    from big_query_wipo_by_family as g
    join big_query_gather_wipo_stats as t on (g.wipo_technology=t.technology)
    where family_id != '-1'
    --group by family_id
);

create table big_query_ai_value (
    family_id varchar(32) primary key, -- eg. US9923222B1
    value double precision not null
);

-- need to deal with missing values
insert into big_query_ai_value (family_id,value) (
    with average as (
        select
            sum(means_present)::double precision/count(means_present) as avg_means_present,
            sum(length_smallest_ind_claim)::double precision/count(length_smallest_ind_claim) as avg_length_smallest_ind_claim,
            sum(num_claims)::double precision/count(num_claims) as avg_num_claims,
            sum(num_assignments)::double precision/count(num_assignments) as avg_num_assignments,
            sum(num_rcites)::double precision/count(num_rcites) as avg_num_rcites,
            sum(family_size)::double precision/count(family_size) as avg_family_size
        from big_query_ai_value_all
    ),
    weights as (
        select weights from big_query_ai_value_weights order by date desc nulls last limit 1
    ), intercept as (
        select intercept from big_query_ai_value_weights order by date desc nulls last limit 1
    )
    select ai.family_id,(predict((
        Array[
            coalesce(means_present,average.avg_means_present),
            coalesce(num_claims,average.avg_num_claims),
            coalesce(num_assignments,average.avg_num_assignments),
            coalesce(family_size,average.avg_family_size),
            coalesce(length_smallest_ind_claim,average.avg_length_smallest_ind_claim),
            coalesce(num_rcites,average.avg_num_rcites)
         ]
    )::double precision[],weights,intercept)*(0.75))+(0.25*coalesce(cpc_stats.score,0.0)) as value
    from big_query_ai_value_all as ai
    left outer join big_query_gather_wipo_stats_by_family_id as cpc_stats
       on (cpc_stats.family_id=ai.family_id),
    average,weights,intercept
);
