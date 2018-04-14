\connect patentdb

create table big_query_compdb_deals (
    deal_id varchar(32) primary key,
    recorded_date date,
    technology text[],
    inactive boolean,
    acquisition boolean,
    reel_frame varchar(50)[] not null
    --buyer text[],
    --seller text[]
);

create table big_query_compdb_deals_by_pub (
    publication_number_full varchar(32) primary key,
    deal_id varchar(32)[],
    recorded_date date[],
    inactive boolean[],
    acquisition boolean[]
    --buyer text[], -- each index can have multiple buyers/sellers (delimited by '; ')
    --seller text[],
);

insert into big_query_compdb_deals_by_pub (publication_number_full,deal_id,recorded_date,inactive,acquisition) (
    select publication_number_full,array_agg(temp.deal_id),array_agg(temp.recorded_date),array_agg(temp.inactive),array_agg(temp.acquisition)
    from (
        select deals.deal_id,deals.recorded_date,deals.inactive,deals.acquisition,rf.reel_frame from big_query_compdb_deals as deals,unnest(deals.reel_frame) with ordinality as rf(reel_frame,n)
    ) as temp
    join big_query_assignment_documentid_by_pub as p on (temp.reel_frame=any(p.reel_frame))
    where p.reel_frame is not null
    group by publication_number_full
);


create table big_query_gather (
    publication_number varchar(32) primary key,
    value integer,
    stage varchar(32)[],
    technology text[]
);

create table big_query_gather_with_pub (
    publication_number_full varchar(32) primary key,
    value integer,
    stage varchar(32)[],
    technology text[]
);

insert into big_query_gather_with_pub (publication_number_full,value,stage,technology) (
    select distinct on (publication_number_full) publication_number_full,g.value,g.stage,g.technology
    from big_query_gather as g
    join patents_global as p on (p.country_code='US' and p.publication_number=g.publication_number)
    where p.country_code='US'
    order by publication_number_full, publication_date desc nulls last
);


create table big_query_gather_cpc (
    publication_number_full varchar(32) primary key,
    tree text[] not null
);

insert into big_query_gather_cpc (publication_number_full,tree) (
    select p.publication_number_full,array_agg(distinct t.tree)
    from big_query_gather as g
    join patents_global as p on (p.country_code='US' and p.publication_number=g.publication_number)
    join (select * from big_query_cpc_tree as c,unnest(c.tree) with ordinality as t(tree,n)) tr on (p.publication_number_full=tr.publication_number_full)
    where p.country_code='US'
    group by p.publication_number_full
);

-- calculate cpc statistics
create table big_query_gather_cpc_stats (
    code varchar(32) primary key,
    frequency double precision not null
);

insert into big_query_gather_cpc_stats (code,frequency) (
    with total as (
        select sum(array_length(tree,1)) from big_query_gather_cpc
    )
    select t.code,((count(*))::double precision)/total
    from big_query_gather_cpc as c,unnest(c.tree) with ordinality as t(code,n)
    group by t.code
);

-- incorporate with value model