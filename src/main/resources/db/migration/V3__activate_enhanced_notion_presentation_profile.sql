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
) values (
    '00000000-0000-0000-0000-000000000001',
    'notion-default',
    2,
    '{"colorMode":"SYSTEM","contentWidth":"STANDARD","density":"COMFORTABLE"}'::jsonb,
    true,
    timestamptz '2026-08-27T00:00:00Z'
);

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
        2,
        'STYLE_SHEET',
        'notion-core',
        1,
        'sha384-V763UM2y9iSN6rUXr+H4a3GeowrAJqJ53QPBQoJQ9/W3UVee9kfeg7pO3tJJ7V/T',
        0
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        2,
        'STYLE_SHEET',
        'katex-styles',
        1,
        'sha384-vlBdW0r3AcZO/HboRPznQNowvexd3fY8qHOWkBi5q7KGgqJ+F48+DceybYmrVbmB',
        1
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        2,
        'STYLE_SHEET',
        'notion-enhancements',
        1,
        'sha384-8FGIk6YzcRYD/nVgH4V1NsyQVFRABYRaeoGSa7h5lcyCrDgneifs9e2TJ3/2zv6b',
        2
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        2,
        'SCRIPT',
        'katex-runtime',
        1,
        'sha384-AtrdNsnxl/75rvBneBVH7DtOvCxSVahR2zWqle1coBKd8DEmLoviqNeJSx64gNAs',
        0
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        2,
        'SCRIPT',
        'notion-tabs',
        1,
        'sha384-VucbIMH0dIpFjnUI6nyjosBUX+cUDRo82zmVz+TihzIdd4C9WwKtpQ1i06jBFgUy',
        1
    ),
    (
        '00000000-0000-0000-0000-000000000001',
        2,
        'SCRIPT',
        'notion-math',
        1,
        'sha384-knQz/ThENhaPoCo7vG4+Zrmeh8Ut2aMQXiEOrgNMT/oxoT8PHnp+G/d+BDgJoScj',
        2
    );

update site_configuration
set presentation_profile_version = 2
where presentation_profile_id = '00000000-0000-0000-0000-000000000001'
  and presentation_profile_version = 1;
