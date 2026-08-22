-- 职业回到「职业范围」粒度，与学历、年薪一致用底部弹层滚轮录入。
--
-- V3 把职业细化到 47 个岗位（规范 3.3 的可搜索下拉形态），实际使用中过细：
-- 档案匹配只需要行业范围，逐个岗位反而增加填写负担。改回规范 3.5 的行业档，
-- 前端三个「范围型」字段（学历 / 职业 / 年薪）统一改用底部弹层滚轮，
-- 不再需要搜索，instructions 里的「可搜索关键词」一并去掉。
--
-- 只改 options_json 与 instructions，不动 data_type（仍是 SINGLE_OPTION）。

UPDATE profile_field_definition
SET options_json = '["互联网 / IT", "金融 / 投资", "教育 / 培训", "医疗 / 健康", "法律 / 咨询", "政府 / 事业单位", "其他行业"]',
    instructions = '请选择职业',
    updated_at = CURRENT_TIMESTAMP
WHERE field_code = 'occupation' AND storage_kind = 'CORE';

-- 旧的岗位取值不在新选项集里，下次保存会被 validateCoreSingleOption 拒掉。
-- 生产库当前 0 份档案，这里只是防御：真有存量就置空，让本人重选。
UPDATE guest_profile
SET occupation = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE occupation IS NOT NULL
  AND occupation NOT IN (
      '互联网 / IT', '金融 / 投资', '教育 / 培训', '医疗 / 健康',
      '法律 / 咨询', '政府 / 事业单位', '其他行业');
