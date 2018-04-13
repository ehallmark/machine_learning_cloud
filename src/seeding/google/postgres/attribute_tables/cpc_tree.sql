\connect patentdb

create table big_query_cpc_tree (
    publication_number_full varchar(32) primary key,
    tree varchar(32)[] not null
);

insert into big_query_cpc_tree (publication_number_full,tree) (
    select publication_number_full,array_agg(distinct tr.tree) from
    patents_global as p,unnest(p.code) with ordinality as c(code,n)
    join big_query_cpc_definition as t on (c.code=t.code), unnest(t.tree) with ordinality as tr(tree,n)
    group by publication_number_full
);