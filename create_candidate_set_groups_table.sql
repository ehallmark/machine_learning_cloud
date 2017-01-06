\connect patentdb

DROP TABLE IF EXISTS candidate_set_groups;
CREATE TABLE candidate_set_groups (
    group_prefix varchar(255) primary key
);
