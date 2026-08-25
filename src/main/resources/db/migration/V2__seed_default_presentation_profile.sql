insert into presentation_profile (
    presentation_profile_id,
    profile_key,
    version,
    token_json,
    is_current,
    created_at
) values (
    '00000000-0000-0000-0000-000000000001',
    'notion-default',
    1,
    '{"colorMode":"SYSTEM","contentWidth":"STANDARD","density":"COMFORTABLE"}'::jsonb,
    true,
    timestamptz '2026-08-25T00:00:00Z'
);

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
    1,
    'STYLE_SHEET',
    'notion-core',
    1,
    'sha384-V763UM2y9iSN6rUXr+H4a3GeowrAJqJ53QPBQoJQ9/W3UVee9kfeg7pO3tJJ7V/T',
    0
);

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
    1,
    'SCRIPT',
    'notion-tabs',
    1,
    'sha384-VucbIMH0dIpFjnUI6nyjosBUX+cUDRo82zmVz+TihzIdd4C9WwKtpQ1i06jBFgUy',
    0
);

insert into sync_state (
    target_kind,
    target_key,
    last_success_at,
    refresh_after,
    failure_count,
    last_error_kind
) values (
    'SITE_CONFIGURATION',
    'singleton',
    null,
    timestamptz '1970-01-01T00:00:00Z',
    0,
    null
);
