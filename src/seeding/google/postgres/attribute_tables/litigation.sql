
\connect patentdb

create table patexia_litigation (
    case_number varchar(100) primary key,
    case_name text not null,
    plaintiff text not null,
    defendant text not null,
    case_date date not null,
    case_type text not null
);

create index patexia_litigation_plaintiff on patexia_litigation (plaintiff);
create index patexia_litigation_defendant on patexia_litigation (defendant);

create table big_query_litigation (
    case_id varchar(100) primary key,
    case_number varchar(100) not null,
    case_name text not null,
    plaintiff text,
    defendant text,
    filing_date date,
    case_type varchar(100),
    case_text text not null,
    patents varchar(32)[]
)

create table big_query_ptab (
      appeal_no varchar(100),
      interference_no varchar(100),
      patent_no varchar(32),
      pre_grant_publication_no varchar(32),
      application_no varchar(32),
      mailed_date date,
      inventor_last_name text,
      inventor_first_name text,
      case_name text,
      last_modified date,
      doc_type varchar(100),
      status varchar(50),
      image_id varchar(100),
      doc_text text,
      unique (appeal_no,interference_no)
);
