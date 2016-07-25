\connect patentdb;

ALTER TABLE patent_vectors ADD COLUMN is_testing BOOLEAN NOT NULL DEFAULT(FALSE);
ALTER TABLE patent_vectors ADD COLUMN compdb_technologies DOUBLE PRECISION[];
ALTER TABLE patent_vectors ADD COLUMN is_valuable BOOLEAN;

CREATE INDEX is_testing_index ON patent_vectors(is_testing);
CREATE INDEX is_valuable_index ON patent_vectors(is_valuable) WHERE is_valuable IS NOT NULL;
CREATE INDEX non_null_compdb_tech_idx ON patent_vectors(compdb_technologies) WHERE compdb_technologies IS NOT NULL;
