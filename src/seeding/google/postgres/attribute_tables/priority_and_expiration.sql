-- TODO make this a global thing
create table big_query_priority_and_expiration (
    publication_number_full varchar(32) primary key,
    priority_date_est date not null,
    expiration_date_est date not null,
);

create table big_query_expired (
    publication_number_full varchar(32) primary key,
    expired boolean not null
)