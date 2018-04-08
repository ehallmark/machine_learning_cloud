\connect patentdb

create table big_query_ai_value (
    family_id varchar(32) primary key, -- eg. US9923222B1
    value double precision not null
);

