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
    where family_id!='-1'
    group by family_id
);

create table big_query_ai_value_citations (
    family_id varchar(32) primary key,
    num_rcites integer not null
);

insert into big_query_ai_value_citations (
    select family_id,count(distinct rcited_family_id) from (select
        family_id,t2.rcited_family_id from big_query_reverse_citations as t,unnest(t.rcite_family_id) with ordinality as t2(rcited_family_id,n)
    ) temp left outer join patents_global on
    (

        (patents_global.publication_number_full=doc_number_full and not is_filing)
        OR
        (patents_global.application_number_full=doc_number_full and is_filing)

    )
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


create table big_query_ai_value (
    family_id varchar(32) primary key, -- eg. US9923222B1
    values double precision[] not null
);

-- need to deal with missing values
insert into big_query_ai_value (family_id,values) (
    select family_id,(Array[means_present,num_claims,num_assignments,family_size,length_smallest_ind_claim,num_rcites])::double precision[] as values
    from big_query_ai_value_assignments
    left outer join big_query_ai_value_citations using (family_id)
    left outer join big_query_ai_value_claims using (family_id)
    left outer join big_query_ai_value_family_size using(family_id)
);

-- query to get values (need to set weights and intercept)
select family_id, predict(values,weights,intercept) as ai_value from big_query_ai_value;


