
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
      image_id varchar(100) primary key,
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
      doc_text text
);

create index big_query_ptab_app_no_idx on big_query_ptab (application_no);


create table big_query_ptab_by_pub (
      publication_number_full varchar(32) primary key,
      appeal_no varchar(100)[] not null,
      interference_no varchar(100)[] not null,
      mailed_date date[] not null,
      inventor_last_name text[] not null,
      inventor_first_name text[] not null,
      case_name text[] not null,
      doc_type varchar(100)[] not null,
      status varchar(50)[] not null,
      doc_text text[] not null
);


insert into big_query_ptab_by_pub (publication_number_full,appeal_no,interference_no,mailed_date,inventor_last_name,inventor_first_name,case_name,doc_type,status,doc_text) (
    select distinct on (publication_number_full) publication_number_full,appeal_no,interference_no,mailed_date,inventor_last_name,inventor_first_name,case_name,doc_type,status,doc_text
    from (
        select lpad(application_no,8,'0') as doc_number,array_agg(appeal_no) as appeal_no,array_agg(interference_no) as interference_no,array_agg(mailed_date) as mailed_date,array_agg(inventor_last_name) as inventor_last_name,array_agg(inventor_first_name) as inventor_first_name,array_agg(case_name) as case_name,array_agg(doc_type) as doc_type,array_agg(status) as status,array_agg(doc_text) as doc_text
        from big_query_ptab as ptab
        where application_no is not null
        group by lpad(application_no,8,'0')
    ) as a
    inner join patents_global as p on ((p.country_code='US') AND ((p.application_number_formatted = a.doc_number)))
    where p.country_code = 'US' and p.application_number_formatted is not null and family_id!='-1'
    order by publication_number_full,publication_date desc nulls last
);

