update presentation_profile
set is_current = false
where profile_key = 'notion-default'
  and is_current = true;

insert into presentation_profile (
    presentation_profile_id,
    profile_key,
    version,
    token_json,
    is_current,
    created_at
)
select
    presentation_profile_id,
    profile_key,
    3,
    token_json,
    true,
    timestamptz '2026-08-31T00:00:00Z'
from presentation_profile
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and version = 2;

insert into presentation_profile_asset (
    presentation_profile_id,
    presentation_profile_version,
    asset_kind,
    asset_key,
    asset_version,
    integrity,
    position
)
select
    presentation_profile_id,
    3,
    asset_kind,
    asset_key,
    asset_version,
    integrity,
    position
from presentation_profile_asset
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and presentation_profile_version = 2;

insert into presentation_profile_asset (
    presentation_profile_id,
    presentation_profile_version,
    asset_kind,
    asset_key,
    asset_version,
    integrity,
    position
) values (
    '00000000-0000-0000-0000-000000000001',
    3,
    'STYLE_SHEET',
    'notion-database',
    1,
    'sha384-wpjZ1AgfFhNgEf5oXEty9kkNbOZEaD6/03YoArov4Cu6Gb8TH5c4MglrC5tvClEz',
    3
);

update site_configuration
set presentation_profile_version = 3
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and presentation_profile_version in (1, 2);
