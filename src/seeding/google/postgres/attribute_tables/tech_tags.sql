\connect patentdb

create table big_query_technologies (
    family_id varchar(32) primary key, -- eg. US9923222B1
    technology text[] not null
);

-- helper table
create table big_query_technologies_helper (
    code varchar(32) primary key,
    technology text not null
);

insert into big_query_technologies (family_id,technology) (
    select family_id,array_agg(distinct t.technology)
    from big_query_technologies_helper as t
    join (
        select family_id,c.code from patents_global as p,unnest(p.code) with ordinality as c(code,n)
        where p.code is not null and family_id != '-1'
    ) temp
    on (temp.code=t.code)
    group by family_id
);

