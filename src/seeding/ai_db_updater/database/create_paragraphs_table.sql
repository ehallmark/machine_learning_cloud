-- CONNECT TO PROPER DATABASE!
\connect patentdb

-- CREATE THE TABLE FOR PATENT SENTENCE TOKENS
CREATE TABLE paragraph_tokens (
    pub_doc_number varchar(25) not null,
    assignees text[] not null default('{}'::text[]),
    tokens text[] not null,
    randomizer double precision not null default(random())
);

-- TO RANDOMIZE ORDER OF DATA PHYSICALLY ON THE DISK
CREATE INDEX paragraph_tokens_random_idx on paragraph_tokens (randomizer);

CLUSTER paragraph_tokens USING paragraph_tokens_random_idx;
DROP INDEX paragraph_tokens_random_idx;

pg_dump -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/patentdb -t paragraph_tokens > data/paragraph_tokens.dump