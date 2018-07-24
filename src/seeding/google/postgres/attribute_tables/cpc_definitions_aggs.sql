\connect patentdb

-- sets the 'tree' attribute
update big_query_cpc_definition set tree=ARRAY[code]||coalesce(parents,'{}'::varchar[]);
