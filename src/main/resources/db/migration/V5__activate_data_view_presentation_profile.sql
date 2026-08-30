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
    4,
    token_json,
    true,
    timestamptz '2026-08-31T00:00:00Z'
from presentation_profile
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and version = 3;

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
    4,
    asset_kind,
    asset_key,
    asset_version,
    integrity,
    position
from presentation_profile_asset
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and presentation_profile_version = 3
  and not (asset_kind = 'STYLE_SHEET' and asset_key = 'notion-database' and asset_version = 1);

insert into presentation_profile_asset (
    presentation_profile_id,
    presentation_profile_version,
    asset_kind,
    asset_key,
    asset_version,
    integrity,
    position
) values
    (
        '00000000-0000-0000-0000-000000000001',
        4,
        'STYLE_SHEET',
        'notion-database',
        2,
        'sha384-Byoo/32d7jkGQ7JNxVuHliHafA+p+T6pBDKa5QQB7qHS9LR/ibKc9wBtt/XyYJaA',
        3
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        4,
        'SCRIPT',
        'notion-database-behavior',
        1,
        'sha384-lG0eCTZ89mtvozPfU6CHLuBi7bmoXFxZBmN7IgVzOEz+hlWJRvikOx3YyBXdF7Qe',
        3
    );

update site_configuration
set presentation_profile_version = 4
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and presentation_profile_version in (1, 2, 3);
