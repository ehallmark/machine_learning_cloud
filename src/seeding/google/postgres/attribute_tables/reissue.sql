\connect patentdb

drop table big_query_reissue;
create table big_query_reissue (
    publication_number_full varchar(32) primary key,
    original_kind_code varchar(8) not null
);

insert into big_query_reissue (
    select pc.publication_number_full, 'S'
    from big_query_priority_claims_by_pub as pc
    join patents_global as p on (p.publication_number_full=pc.publication_number_full)
    where p.country_code='US' and kind_code like 'E%' and (
        array_to_string(pc.pc_publication_number_full,' ','') like '%USD%'
        or array_to_string(pc.pc_application_number_formatted_with_country, ' ','') like '%US29%'
        or p.application_number_formatted like '29%'
    )
);


insert into big_query_reissue (
    select pc.publication_number_full, 'P'
    from big_query_priority_claims_by_pub as pc
    join patents_global as p on (p.publication_number_full=pc.publication_number_full)
    where p.country_code='US' and kind_code like 'E%' and (
        array_to_string(pc.pc_publication_number_full,' ','') like '%USP%'
    )
) on conflict (publication_number_full) do nothing;


