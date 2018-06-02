-- TODO make this a global thing
create table big_query_priority_and_expiration (
    publication_number_full varchar(32) primary key,
    priority_date date not null,
    priority_date_est date not null,
    expiration_date_est date,
    expiration_reason text
);

insert into big_query_priority_and_expiration (
    select p.publication_number_full,
    coalesce(priority_date, filing_date),
    coalesce(priority_date, filing_date) + interval '1' day * coalesce(term_adjustments,0),
    case when country_code='US'
        then
            case when kind_code like 'S%'
                then publication_date + interval '14 years'
                else coalesce(priority_date, filing_date) + interval '1' day * term_adjustments + interval '20 years'
            end
        else null
    end,
    case when country_code='US'
        then
            case when kind_code like 'S%'
                then 'US Design Patent: Expiration Date = Issued Date + Term Adjustments + 14 years'
                else 'US Patent: Expiration Date = Priority Date + Term Adjustments + 20 years'
            end
        else null
    end
    from patents_global as p
    left outer join big_query_pair_by_pub as pair
    on (p.publication_number_full=pair.publication_number_full)

);
