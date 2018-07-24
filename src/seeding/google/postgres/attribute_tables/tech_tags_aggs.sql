\connect patentdb


-- tweak model
update big_query_technologies set technology = 'DATA MANAGEMENT' where technology = 'DATA GOVERNANCE';
update big_query_technologies set technology = 'TARGETED MEDIA' where technology = 'TACTICAL MEDIA';

update big_query_technologies set secondary = 'DATA MANAGEMENT' where secondary = 'DATA GOVERNANCE';
update big_query_technologies set secondary = 'TARGETED MEDIA' where secondary = 'TACTICAL MEDIA';