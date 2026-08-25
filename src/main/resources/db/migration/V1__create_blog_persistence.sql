create table post (
    post_id uuid primary key,
    title text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint post_title_not_blank_check check (btrim(title) <> '')
);

create table post_source_binding (
    source_id text not null,
    external_id text not null,
    post_id uuid not null references post(post_id),
    primary key (source_id, external_id),
    unique (post_id),
    constraint post_source_binding_source_id_not_blank_check check (btrim(source_id) <> ''),
    constraint post_source_binding_external_id_not_blank_check check (btrim(external_id) <> '')
);

create table post_snapshot (
    post_id uuid primary key references post(post_id),
    snapshot_json jsonb not null,
    source_revision text not null,
    captured_at timestamptz not null,
    constraint post_snapshot_source_revision_not_blank_check check (btrim(source_revision) <> ''),
    constraint post_snapshot_json_object_check check (jsonb_typeof(snapshot_json) = 'object')
);

create table post_availability (
    post_id uuid primary key references post(post_id),
    status text not null,
    confirmed_at timestamptz not null,
    constraint post_availability_status_check check (status in ('PUBLISHED', 'UNPUBLISHED'))
);

create table publication (
    publication_id uuid primary key,
    root_post_id uuid references post(post_id),
    active_revision_id uuid,
    constraint publication_root_and_active_revision_together_check check (
        (root_post_id is null) = (active_revision_id is null)
    )
);

create table publication_revision (
    revision_id uuid primary key,
    publication_id uuid not null references publication(publication_id),
    state text not null,
    started_at timestamptz not null,
    activated_at timestamptz,
    constraint publication_revision_state_check check (state in ('STAGING', 'ACTIVE', 'SUPERSEDED', 'ABANDONED')),
    constraint publication_revision_activation_timestamp_check check (
        (state in ('ACTIVE', 'SUPERSEDED') and activated_at is not null) or
        (state in ('STAGING', 'ABANDONED') and activated_at is null)
    ),
    unique (publication_id, revision_id)
);

create unique index publication_revision_one_active_per_publication
    on publication_revision (publication_id)
    where state = 'ACTIVE';

alter table publication
    add constraint publication_active_revision_ownership_fkey
        foreign key (publication_id, active_revision_id)
        references publication_revision(publication_id, revision_id)
        deferrable initially immediate;

create table publication_member (
    revision_id uuid not null references publication_revision(revision_id),
    post_id uuid not null references post(post_id),
    parent_post_id uuid,
    depth integer not null,
    primary key (revision_id, post_id),
    constraint publication_member_parent_in_same_revision_fkey
        foreign key (revision_id, parent_post_id)
        references publication_member(revision_id, post_id)
        deferrable initially immediate,
    constraint publication_member_depth_check check (depth >= 0),
    constraint publication_member_root_depth_check check (
        (parent_post_id is null and depth = 0) or (parent_post_id is not null and depth > 0)
    )
);

create unique index publication_member_one_root_per_revision
    on publication_member (revision_id)
    where parent_post_id is null;

create index publication_member_post_id_index on publication_member (post_id);

create table presentation_profile (
    presentation_profile_id uuid not null,
    profile_key text not null,
    version bigint not null,
    token_json jsonb not null,
    is_current boolean not null,
    created_at timestamptz not null,
    primary key (presentation_profile_id, version),
    constraint presentation_profile_version_check check (version >= 0),
    unique (profile_key, version),
    constraint presentation_profile_key_not_blank_check check (btrim(profile_key) <> ''),
    constraint presentation_profile_token_json_object_check check (jsonb_typeof(token_json) = 'object')
);

create unique index presentation_profile_one_current_per_key
    on presentation_profile (profile_key)
    where is_current;

create table presentation_profile_asset (
    presentation_profile_id uuid not null,
    presentation_profile_version bigint not null,
    asset_kind text not null,
    asset_key text not null,
    asset_version bigint not null,
    integrity text not null,
    position integer not null,
    primary key (presentation_profile_id, presentation_profile_version, asset_kind, position),
    constraint presentation_profile_asset_profile_fkey
        foreign key (presentation_profile_id, presentation_profile_version)
        references presentation_profile(presentation_profile_id, version),
    constraint presentation_profile_asset_kind_check check (asset_kind in ('STYLE_SHEET', 'SCRIPT')),
    constraint presentation_profile_asset_key_not_blank_check check (btrim(asset_key) <> ''),
    constraint presentation_profile_asset_version_check check (asset_version >= 0),
    constraint presentation_profile_asset_position_check check (position >= 0),
    constraint presentation_profile_asset_integrity_not_blank_check check (btrim(integrity) <> '')
);

create table site_configuration (
    site_id smallint primary key,
    publication_id uuid not null unique references publication(publication_id),
    root_source_id text not null,
    root_external_id text not null,
    header_source_id text,
    header_external_id text,
    footer_source_id text,
    footer_external_id text,
    metadata_json jsonb not null,
    presentation_profile_id uuid not null,
    presentation_profile_version bigint not null,
    synced_at timestamptz not null,
    constraint site_configuration_singleton_check check (site_id = 1),
    constraint site_configuration_root_source_id_not_blank_check check (btrim(root_source_id) <> ''),
    constraint site_configuration_root_external_id_not_blank_check check (btrim(root_external_id) <> ''),
    constraint site_configuration_metadata_json_object_check check (jsonb_typeof(metadata_json) = 'object'),
    constraint site_configuration_header_source_reference_check check (
        (header_source_id is null) = (header_external_id is null)
    ),
    constraint site_configuration_footer_source_reference_check check (
        (footer_source_id is null) = (footer_external_id is null)
    ),
    constraint site_configuration_presentation_profile_fkey
        foreign key (presentation_profile_id, presentation_profile_version)
        references presentation_profile(presentation_profile_id, version)
);

create table sync_state (
    target_kind text not null,
    target_key text not null,
    last_success_at timestamptz,
    refresh_after timestamptz not null,
    failure_count integer not null,
    last_error_kind text,
    primary key (target_kind, target_key),
    constraint sync_state_target_kind_check check (target_kind in ('SITE_CONFIGURATION', 'PUBLICATION', 'POST')),
    constraint sync_state_target_key_not_blank_check check (btrim(target_key) <> ''),
    constraint sync_state_failure_count_check check (failure_count >= 0),
    constraint sync_state_last_error_kind_check check (
        last_error_kind is null or last_error_kind in ('RETRYABLE_SOURCE', 'AUTHENTICATION', 'ACCESS', 'CONFIGURATION', 'MAPPING')
    ),
    constraint sync_state_failure_error_pair_check check (
        (failure_count = 0) = (last_error_kind is null)
    )
);

create index sync_state_refresh_after_target_index on sync_state (refresh_after, target_kind, target_key);
