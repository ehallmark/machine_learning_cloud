\connect patentdb

create table big_query_ai_value (
    family_id varchar(32) primary key, -- eg. US9923222B1
    values double precision[] not null
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

-- query to get values (need to set weights and intercept)
select family_id, predict(values,weights,intercept) as ai_value from big_query_ai_value;


