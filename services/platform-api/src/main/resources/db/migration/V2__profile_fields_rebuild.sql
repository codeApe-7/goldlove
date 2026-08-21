-- 档案字段重建：让后端字段定义与《H5 表单组件与下拉样式规范 v1.0》的组件形态一致。
--
-- 规范图第 3 区块为学历、职业、年薪、性别指定了固定选项集，V1 的种子数据对不上：
-- 学历与职业是自由文本、年薪档位划分不同、性别缺「不公开」。存量取值与新选项集冲突，
-- 且服务端要开始强校验这些字段，因此**按授权清空全部用户数据后重建字段定义**。
--
-- Flyway 以 archive_owner 执行。本次不新增表，无需追加授权。

-- ==========================================================================
-- 清空全部用户数据
-- ==========================================================================

-- authorization_record 与 audit_log 有拒绝 UPDATE / DELETE 的行级触发器，
-- 但 TRUNCATE 不触发行级触发器（只触发语句级 TRUNCATE 触发器），所以这里能清掉。
-- CASCADE 会连带清空所有通过外键引用这些表的表；仍然显式列出全部表名，
-- 让「到底删了什么」在迁移脚本里一目了然，而不是靠 CASCADE 隐式推导。
TRUNCATE TABLE
    profile_field_value,
    profile_photo,
    guest_profile,
    authorization_record,
    payment_order,
    payment_record,
    activation_code,
    audit_log,
    user_account
    RESTART IDENTITY CASCADE;

-- ==========================================================================
-- 重建核心字段定义
-- ==========================================================================

-- 用 DELETE + INSERT 而不是逐条 UPDATE：preserve_profile_field_definition_identity()
-- 会在 ever_used 为真时锁死 data_type，UPDATE 路径要处处防御；
-- profile_field_value 已清空，没有任何行引用这些定义，直接重建最干净。
DELETE FROM profile_field_definition;

INSERT INTO profile_field_definition (
    field_code, label, storage_kind, data_type, required, enabled, options_json,
    sort_order, instructions
) VALUES
    ('gender', '性别', 'CORE', 'SINGLE_OPTION', TRUE, TRUE,
        '["男", "女", "不公开"]',
        10, '性别信息仅自己可见，用于系统匹配参考'),
    ('birth_date', '出生日期', 'CORE', 'DATE', TRUE, TRUE, NULL,
        20, '请选择出生日期'),
    ('height_cm', '身高（厘米）', 'CORE', 'INTEGER', TRUE, TRUE, NULL,
        30, '请输入身高'),
    ('education', '学历', 'CORE', 'SINGLE_OPTION', TRUE, TRUE,
        '["博士及以上", "硕士研究生", "大学本科", "大专", "高中及以下", "其他学历"]',
        40, '请选择学历'),
    ('occupation', '职业', 'CORE', 'SINGLE_OPTION', TRUE, TRUE,
        '["互联网 / IT", "金融 / 投资", "教育 / 培训", "医疗 / 健康", "法律 / 咨询", "政府 / 事业单位", "其他行业"]',
        50, '请选择职业'),
    -- 「保密」不在规范图的 6 档里，但档位本身是敏感信息，允许用户不公开；
    -- 前端选中后加锁标且不展示具体区间。
    ('income_range', '年薪', 'CORE', 'SINGLE_OPTION', TRUE, TRUE,
        '["20万以下", "20万-30万", "30万-50万", "50万-80万", "80万-120万", "120万以上", "保密"]',
        60, '请选择年薪 / 收入区间'),
    -- 所在城市保持 TEXT：规范 3.8 的省 / 市 / 区三级联动是前端录入方式，
    -- 数据集在 apps/guest-app/src/data/regions.data.ts，结果按「省 / 市 / 区」拼成文本存回，
    -- 后端不维护地区表。
    ('city', '所在城市', 'CORE', 'TEXT', TRUE, TRUE, NULL,
        70, '请选择所在城市 / 地区'),
    ('wechat_id', '微信号', 'CORE', 'TEXT', TRUE, TRUE, NULL,
        80, '请输入微信号'),
    ('douyin_id', '抖音号', 'CORE', 'TEXT', FALSE, TRUE, NULL,
        90, '选填，请输入抖音号'),
    ('douyin_nickname', '抖音昵称', 'CORE', 'TEXT', FALSE, TRUE, NULL,
        100, '选填，请输入抖音昵称'),
    ('douyin_profile_url', '抖音主页链接', 'CORE', 'TEXT', FALSE, TRUE, NULL,
        110, '选填，请输入抖音主页链接');
