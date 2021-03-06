\connect patentdb

-- COMPDB QUERIES
drop table big_query_compdb_reel_frames;
create table big_query_compdb_reel_frames (
    reel_frame varchar(32) primary key,
    deal_id varchar(32) not null,
    date date,
    technology text[],
    inactive boolean,
    acquisition boolean
);

insert into big_query_compdb_reel_frames (reel_frame,deal_id,date,technology,inactive,acquisition) (
    select r.reel_frame,deal_id,recorded_date,technology,inactive,acquisition
    from big_query_compdb_deals as c, unnest(c.reel_frame) with ordinality as r(reel_frame,n)
);

drop table big_query_compdb_deals_by_pub;
create table big_query_compdb_deals_by_pub (
    publication_number_full varchar(32) primary key,
    deal_id varchar(32)[],
    recorded_date date[],
    technology text[], -- each index can have multiple technologies (delimited by '; ')
    inactive boolean[],
    acquisition boolean[]
);

insert into big_query_compdb_deals_by_pub (publication_number_full,deal_id,recorded_date,technology,inactive,acquisition) (
    select publication_number_full,array_agg(temp.deal_id),array_agg(temp.date),array_agg(array_to_string(temp.technology,'; ')),array_agg(temp.inactive),array_agg(temp.acquisition)
    from big_query_compdb_reel_frames as temp
    join (
        select p.publication_number_full,r.reel_frame from big_query_assignment_documentid_by_pub as p, unnest(p.reel_frame) with ordinality as r(reel_frame,n)
        where p.reel_frame is not null
    ) as temp2
    on (temp.reel_frame=temp2.reel_frame)
    group by publication_number_full
);


-- START OF GATHER QUERIES
drop table big_query_gather_with_pub;
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


drop table big_query_gather_wipo;
create table big_query_gather_wipo (
    publication_number_full varchar(32) primary key,
    technology text not null
);

insert into big_query_gather_wipo (publication_number_full,technology) (
    select p.publication_number_full,mode() within group (order by wipo_tech)
    from big_query_gather as g
    join patents_global as p on (p.country_code='US' and p.publication_number=g.publication_number)
    join big_query_wipo_by_family as wipo on (wipo.family_id=p.family_id), unnest(wipo.wipo_technology) as wipo_tech
    where p.country_code='US'
    group by p.publication_number_full
);

-- calculate cpc statistics
drop table big_query_gather_wipo_stats;
create table big_query_gather_wipo_stats (
    technology text primary key,
    frequency double precision not null
);

insert into big_query_gather_wipo_stats (technology,frequency) (
    select t.technology,((count(*))::double precision)/(select count(*) from big_query_gather_wipo)
    from big_query_gather_wipo as t
    group by t.technology
);

