create table notion_page (
    page_id text primary key,
    title text not null,
    notion_url text not null,
    public_url text,
    visibility text not null,
    notion_last_edited_at timestamptz,
    last_synced_at timestamptz,
    refresh_after timestamptz not null,
    failure_count integer not null default 0,
    last_error text,
    constraint notion_page_visibility_check check (visibility in ('DISCOVERED', 'PUBLIC', 'PRIVATE')),
    constraint notion_page_failure_count_check check (failure_count >= 0),
    constraint notion_page_public_url_check check (
        (visibility = 'PUBLIC' and public_url is not null) or
        (visibility <> 'PUBLIC' and public_url is null)
    )
);

create table site_settings (
    id bigint generated always as identity primary key,
    settings_data_source_id text not null unique,
    root_page_id text not null,
    header_page_id text,
    footer_page_id text,
    head_json jsonb not null default '{}'::jsonb,
    last_synced_at timestamptz,
    refresh_after timestamptz not null,
    last_error text
);

create table page_snapshot (
    page_id text primary key references notion_page(page_id) on delete cascade,
    snapshot_json jsonb not null,
    notion_last_edited_at timestamptz not null,
    captured_at timestamptz not null
);

create table page_route (
    path text primary key,
    page_id text not null references notion_page(page_id) on delete cascade,
    kind text not null,
    active boolean not null default true,
    created_at timestamptz not null,
    constraint page_route_path_check check (path like '/%'),
    constraint page_route_kind_check check (kind in ('ROOT', 'CANONICAL', 'ALIAS')),
    constraint page_route_root_path_check check ((kind = 'ROOT') = (path = '/'))
);

create unique index page_route_active_canonical_per_page
    on page_route (page_id)
    where active and kind = 'CANONICAL';

create unique index page_route_active_root
    on page_route (kind)
    where active and kind = 'ROOT';

create index notion_page_refresh_after_index on notion_page (refresh_after);
create index site_settings_refresh_after_index on site_settings (refresh_after);
