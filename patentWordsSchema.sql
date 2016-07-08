\connect patentdb

CREATE TABLE patent_words (
	word varchar(255) PRIMARY KEY,
	count integer DEFAULT 0
);