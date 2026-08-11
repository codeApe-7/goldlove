INSERT INTO profile_field_definition (
    field_code,
    label,
    storage_kind,
    data_type,
    required,
    enabled,
    ever_used,
    options_json,
    sort_order,
    instructions,
    version,
    created_at,
    updated_at
) VALUES
    ('wechat_id', '微信号', 'CORE', 'TEXT', TRUE, TRUE, FALSE, NULL, 80, '请输入微信号', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_id', '抖音号', 'CORE', 'TEXT', FALSE, TRUE, FALSE, NULL, 90, '选填，请输入抖音号', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_nickname', '抖音昵称', 'CORE', 'TEXT', FALSE, TRUE, FALSE, NULL, 100, '选填，请输入抖音昵称', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_profile_url', '抖音主页链接', 'CORE', 'TEXT', FALSE, TRUE, FALSE, NULL, 110, '选填，请输入抖音主页链接', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (field_code) DO UPDATE SET
    label = EXCLUDED.label,
    storage_kind = EXCLUDED.storage_kind,
    data_type = EXCLUDED.data_type,
    required = EXCLUDED.required,
    enabled = EXCLUDED.enabled,
    sort_order = EXCLUDED.sort_order,
    instructions = EXCLUDED.instructions,
    updated_at = CURRENT_TIMESTAMP;
