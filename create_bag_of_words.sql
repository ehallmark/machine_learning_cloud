\connect patentdb

CREATE TABLE bag_of_words (
    name VARCHAR(50) PRIMARY KEY,
    num_words integer not null,
    bow integer[] not null
);
