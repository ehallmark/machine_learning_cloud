\connect patentdb

create table big_query_technologies (
    family_id varchar(32) primary key, -- eg. US9923222B1
    technology text[] not null
);

