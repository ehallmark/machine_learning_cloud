-- CONNECT TO PROPER DATABASE!
\connect patentdb

-- CREATE THE TABLE FOR PATENT SENTENCE TOKENS
CREATE TABLE patent_sentence_tokens (
    pub_doc_number varchar(25) not null,
    tokens text[] not null,
    doc_type varchar(50) check (doc_type is not null and doc_type in ('claim','description','abstract')),
    randomizer double precision not null default(random())
);

-- TO RANDOMIZE ORDER OF DATA PHYSICALLY ON THE DISK
CREATE INDEX patent_sentence_tokens_random_idx on patent_sentence_tokens (randomizer);
CLUSTER patent_sentence_tokens USING patent_sentence_tokens_random_idx;