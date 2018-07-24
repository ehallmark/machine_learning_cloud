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
    publication_number_full varchar(32) primary key,
    means_present integer,
    num_claims integer,
    length_smallest_ind_claim integer
); -- insert script is located in patent_text.sql

insert into big_query_ai_value_claims (
    select publication_number_full,
    coalesce(mode() within group(order by means_present), case when bool_and(case when claim ~ 'claim [0-9]' OR claim like '%(canceled)%' OR char_length(claim)<5 then null else (claim like '% means %')::boolean end) then 1 else 0 end),
    coalesce(mode() within group(order by num_claims), count(*)),
    coalesce(mode() within group(order by length_smallest_ind_claim), min(case when claim ~ 'claim [0-9]' OR claim like '%(canceled)%' OR char_length(claim)<5 then null else array_length(array_remove(regexp_split_to_array(claim,'\s+'),''),1) end))
    from big_query_patent_english_claims as p, unnest(regexp_split_to_array(claims, '((Iaddend..Iadd.)|(\n\s*\n\s*\n\s*))')) with ordinality as c(claim,n)
    where num_claims is not null or (claim is not null and char_length(trim(claim))>20)
    group by publication_number_full
);


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
    where family_id !='-1'
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


drop table big_query_ai_value_all;
create table big_query_ai_value_all (
    publication_number_full varchar(32) primary key,
    means_present integer,
    num_claims integer,
    length_smallest_ind_claim integer,
    num_rcites integer,
    num_assignments integer,
    family_size integer
);

insert into big_query_ai_value_all (publication_number_full,means_present,num_claims,length_smallest_ind_claim,num_rcites,num_assignments,family_size) (
    select
    p.publication_number_full,
    means_present,
    num_claims,
    length_smallest_ind_claim,
    num_rcites,
    num_assignments,
    family_size
    from big_query_family_id as p
    join big_query_ai_value_family_size as f on (f.family_id=p.family_id)
    left outer join big_query_ai_value_citations as ci on (ci.family_id=p.family_id)
    left outer join big_query_ai_value_claims as cl on (p.publication_number_full=cl.publication_number_full)
    left outer join big_query_ai_value_assignments as a on (a.family_id=p.family_id)
);

drop table big_query_ai_value_weights;
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

drop table big_query_gather_wipo_stats_by_pub;
create table big_query_gather_wipo_stats_by_pub (
    publication_number_full varchar(32) primary key,
    score double precision not null
);

insert into big_query_gather_wipo_stats_by_pub (publication_number_full,score) (
    select f.publication_number_full,(frequency)*(select max(frequency) as max from big_query_gather_wipo_stats) as score
    from big_query_family_id as f
    join big_query_wipo_by_family as g on (f.family_id=g.family_id)
    join big_query_gather_wipo_stats as t on (g.wipo_technology[1]=t.technology)
    where f.family_id != '-1'
);


drop table big_query_ai_value;
create table big_query_ai_value (
    publication_number_full varchar(32) primary key, -- eg. US9923222B1
    value double precision not null
);

-- need to deal with missing values
insert into big_query_ai_value (publication_number_full,value) (
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
    select ai.publication_number_full,(predict((
        Array[
            coalesce(means_present,average.avg_means_present),
            coalesce(num_claims,average.avg_num_claims),
            coalesce(num_assignments,average.avg_num_assignments),
            coalesce(family_size,average.avg_family_size),
            coalesce(length_smallest_ind_claim,average.avg_length_smallest_ind_claim),
            coalesce(num_rcites,average.avg_num_rcites)
         ]
    )::double precision[],weights,intercept)*(0.85))+(0.15*coalesce(cpc_stats.score,0.0)) as value
    from big_query_ai_value_all as ai
    left outer join big_query_gather_wipo_stats_by_pub as cpc_stats
       on (cpc_stats.publication_number_full=ai.publication_number_full),
    average,weights,intercept
);
