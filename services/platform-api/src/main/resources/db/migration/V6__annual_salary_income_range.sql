-- 收入范围改为年薪选项，滚动选择
UPDATE profile_field_definition
SET label = '年薪',
    data_type = 'SINGLE_OPTION',
    options_json = '["小于10万","10-20万","20-30万","30-50万","50-100万","100万以上","保密"]',
    instructions = '请选择年薪范围'
WHERE field_code = 'income_range';
