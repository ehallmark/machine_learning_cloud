\connect patentdb


drop table big_query_ptab_by_pub;
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
