\connect patentdb

create table big_query_ai_value_family_size (
    family_id varchar(32) primary key,
    family_size integer not null
);

insert into big_query_ai_value_family_size (family_id,family_size) (
    select family_id,count(*) from patents_global group by family_id
);

create table big_query_ai_value_claims (
    family_id varchar(32) primary key,
    means_present integer,
    num_claims integer,
    num_ind_claims integer,
    length_smallest_ind_claim integer
);

create table big_query_ai_value_assignments (
    family_id varchar(32) primary key,
    num_assignments integer,
    num_security_interests integer,
    num_assignors_interest integer
);

create table big_query_ai_value_citations (
    family_id varchar(32) primary key,
    num_rcites integer,
    num_countries_cited integer
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
    values double precision[] not null
);

-- need to deal with missing values
insert into big_query_ai_value (family_id,values) (
    select family_id,(Array[means_present,num_claims,num_assignments,family_size,length_smallest_ind_claim,num_rcites])::double precision[] as values
    from big_query_ai_value_assignments
    left join big_query_ai_value_citations using (family_id)
    left join big_query_ai_value_claims using (family_id)
    left join big_query_ai_value_family_size using(family_id)
);

-- query to get values (need to set weights and intercept)
select family_id, predict(values,weights,intercept) as ai_value from big_query_ai_value;


