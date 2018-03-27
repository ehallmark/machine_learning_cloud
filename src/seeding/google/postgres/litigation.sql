
\connect patentdb

create table patexia_litigation (
    case_number varchar(100) primary key,
    case_name text not null,
    plaintiff text not null,
    defendant text not null,
    case_date date not null
);

create index patexia_litigation_plaintiff on patexia_litigation (plaintiff);
create index patexia_litigation_defendant on patexia_litigation (defendant);