\connect patentdb

CREATE TABLE bag_of_words (
    name VARCHAR(20) PRIMARY KEY,
    bow integer[] not null
);
