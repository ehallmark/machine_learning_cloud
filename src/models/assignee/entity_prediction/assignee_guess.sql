

create table assignees_inventors (
    assignee text not null,
    inventor text not null,
    date date not null,
    primary key(assignee, inventor, date)
);

insert into assignees_inventors (
    select a.assignee,i.inventor,filing_date
    from patents_global as p,
        unnest(p.assignee_harmonized,p.inventor_harmonized) as a(assignee),
        unnest(p.inventor_harmonized) as i(inventor)
    where a.assignee is not null and i.inventor is not null and filing_date is not null
) on conflict do nothing;


