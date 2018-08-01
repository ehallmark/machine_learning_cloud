\connect patentdb

-- tweak model


update big_query_technologies2 set (technology,technology2) = ('DESIGN','DESIGN') where family_id in (
    select distinct f.family_id from big_query_family_id as f
    join big_query_technologies as e on (e.family_id=f.family_id)
    where e.family_id!='-1' and (kind_code like 'S%')
);

update big_query_technologies2 set (technology,technology2) = ('BOTANY','PLANTS') where family_id in (
    select distinct f.family_id from big_query_family_id as f
    join big_query_technologies as e on (e.family_id=f.family_id)
    where e.family_id!='-1' and (kind_code like 'P%')
);


update big_query_technologies2 set technology = 'DATA MANAGEMENT' where technology = 'DATA GOVERNANCE';
update big_query_technologies2 set technology = 'TARGETED MEDIA' where technology = 'TACTICAL MEDIA';

update big_query_technologies2 set technology2 = 'DATA MANAGEMENT' where technology2 = 'DATA GOVERNANCE';
update big_query_technologies2 set technology2 = 'TARGETED MEDIA' where technology2 = 'TACTICAL MEDIA';

