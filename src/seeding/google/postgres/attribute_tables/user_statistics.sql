
drop table user_searches;
create table user_searches (
    username text not null,
    login_time timestamp not null default(now())
);

drop table user_logins;
create table user_logins (
    username text not null,
    login_time timestamp not null default(now())
);