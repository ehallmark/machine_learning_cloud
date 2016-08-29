\connect patentdb

CREATE TABLE bag_of_words (
    name VARCHAR(20) PRIMARY KEY,
    bow integer[] not null
);

CREATE OR REPLACE FUNCTION vec_add(arr1 numeric[], arr2 numeric[])
RETURNS numeric[] AS
$$
SELECT array_agg(result)
FROM (SELECT tuple.val1 + tuple.val2 AS result
      FROM (SELECT UNNEST($1) AS val1
                   ,UNNEST($2) AS val2
                   ,generate_subscripts($1, 1) AS ix) tuple
      ORDER BY ix) inn;
$$ LANGUAGE SQL IMMUTABLE STRICT;

CREATE OR REPLACE FUNCTION vec_add(arr1 int[], arr2 int[])
RETURNS int[] AS
$$
SELECT array_agg(result)
FROM (SELECT tuple.val1 + tuple.val2 AS result
      FROM (SELECT UNNEST($1) AS val1
                   ,UNNEST($2) AS val2
                   ,generate_subscripts($1, 1) AS ix) tuple
      ORDER BY ix) inn;
$$ LANGUAGE SQL IMMUTABLE STRICT;
