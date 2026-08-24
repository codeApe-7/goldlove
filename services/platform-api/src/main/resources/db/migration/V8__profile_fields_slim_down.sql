-- 档案字段收敛：出生日期换成年龄，抖音昵称与主页链接下线。
--
-- Flyway 以 archive_owner 执行。本次不新增表，无需追加授权
-- （guest_profile 与 profile_field_definition 的权限 V1 已给足）。
--
-- 生活照上限从 6 张收到 3 张是**应用层**校验（GuestProfileDraftService.validatePhotos），
-- 库里从来没有对应约束（profile_photo 只保证「每档案最多一张头像」），所以这里没有它的份。

-- ==========================================================================
-- guest_profile：birth_date → age
-- ==========================================================================

-- 只收集年龄。出生日期是精确到天的身份识别信息，而档案实际用到的只有「多大」，
-- 于是这一列既超出了用途，又是遮挡名单上的一项。
ALTER TABLE guest_profile ADD COLUMN age INTEGER;

-- 存量按出生日期折算成当下的周岁。算出来落在合理区间外的**留空**，不夹到边界：
-- 出生日期马上就要删掉，凭一条明显不对的数据编个年龄，比让用户重填一次更糟。
-- 必填校验会把这些档案标成「还缺年龄」，用户下次保存档案时补上。
UPDATE guest_profile
   SET age = date_part('year', age(CURRENT_DATE, birth_date))::INTEGER
 WHERE birth_date IS NOT NULL
   AND date_part('year', age(CURRENT_DATE, birth_date)) BETWEEN 18 AND 100;

-- 草稿可以还没填年龄，所以允许 NULL；填了就必须是个人能有的岁数。
-- 应用层用同一个区间校验（normalizeAndValidate），库里这条是兜底。
ALTER TABLE guest_profile
    ADD CONSTRAINT ck_guest_profile_age CHECK (age IS NULL OR age BETWEEN 18 AND 100);

ALTER TABLE guest_profile DROP COLUMN birth_date;

-- ==========================================================================
-- guest_profile：抖音昵称与主页链接下线
-- ==========================================================================

-- 抖音号（douyin_id）保留：授权书里点名的是它，运营也靠它找人。
-- 昵称与主页链接都能由抖音号推出来，收集它们只是多存两份可识别身份的信息。
ALTER TABLE guest_profile
    DROP COLUMN douyin_nickname,
    DROP COLUMN douyin_profile_url;

-- ==========================================================================
-- 字段定义
-- ==========================================================================

-- CORE 字段的取值落在 guest_profile 的具名列上，从不写进 profile_field_value，
-- 因此这三行没有任何值引用，可以直接删（ever_used 也一直是 FALSE）。
-- 用 DELETE + INSERT 而不是把 birth_date 改名成 age：
-- preserve_profile_field_definition_identity() 会在 ever_used 为真时锁死 data_type，
-- 而 DATE → INTEGER 正是它要拦的那种改动。
DELETE FROM profile_field_definition
 WHERE field_code IN ('birth_date', 'douyin_nickname', 'douyin_profile_url');

-- sort_order 沿用 birth_date 的 20，年龄在表单里还是排在性别后面。
INSERT INTO profile_field_definition (
    field_code, label, storage_kind, data_type, required, enabled, options_json,
    sort_order, instructions
) VALUES
    ('age', '年龄', 'CORE', 'INTEGER', TRUE, TRUE, NULL, 20, '请输入年龄（周岁）');
