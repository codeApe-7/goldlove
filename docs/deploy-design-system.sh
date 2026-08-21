#!/usr/bin/env bash
# 部署「H5 表单组件与下拉样式规范 v1.0」设计系统改造。
# 在服务器上执行（宝塔面板 → 终端，或 ssh root@8.218.225.226）。
#
# ⚠️ 第 3 步的 V2 迁移会 TRUNCATE 全部用户数据：账号、档案、照片引用、付款记录、
#    订单、激活码、同意记录、审计日志。admin_user 与授权书保留。
#    按 2026-08-21 的决策不做备份——确认这是你要的再往下走。
#
# 与上次上线的区别：不动数据卷（不 down -v）、.env 无需改动、管理员无需重建。
set -euo pipefail

cd /opt/love
BRANCH=feat/identity-foundation

echo "===== 1. 拉取新代码 ====="
git fetch origin
git checkout -f "$BRANCH" 2>/dev/null || git checkout -B "$BRANCH" "origin/$BRANCH"
git reset --hard "origin/$BRANCH"
git log --oneline -5

echo
echo "===== 2. 迁移前的数据量（这些即将被清空，记下来） ====="
docker compose exec -T postgres psql -U archive_owner -d marriage_archive -c "
  SELECT (SELECT count(*) FROM user_account)         AS accounts,
         (SELECT count(*) FROM guest_profile)        AS profiles,
         (SELECT count(*) FROM profile_photo)        AS photos,
         (SELECT count(*) FROM payment_record)       AS payments,
         (SELECT count(*) FROM payment_order)        AS orders,
         (SELECT count(*) FROM activation_code)      AS codes,
         (SELECT count(*) FROM authorization_record) AS consents,
         (SELECT count(*) FROM audit_log)            AS audit;"
docker compose exec -T postgres psql -U archive_owner -d marriage_archive \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

echo
# 交互式跑会问一次；非交互（ssh 'bash -s'、CI）必须显式 CONFIRM=yes，宁可失败也不误清。
if [ "${CONFIRM:-}" = "yes" ]; then
  echo "CONFIRM=yes，跳过交互确认，直接清空。"
elif [ -t 0 ]; then
  read -r -p "以上数据将被清空，继续？输入 yes 回车：" ANSWER
  [ "$ANSWER" = "yes" ] || { echo "已中止，未做任何改动。"; exit 1; }
else
  echo "非交互环境且未设置 CONFIRM=yes，已中止（未做任何改动）。"
  echo "确认要清库请用： CONFIRM=yes bash docs/deploy-design-system.sh"
  exit 1
fi

echo
echo "===== 3. 跑 Flyway 迁移（应用 V2：清数据 + 重建字段定义） ====="
docker compose run --rm migrate

echo
echo "===== 4. 清空 Redis 会话 ====="
# 必须做：guest 会话的 sa-token loginId 就是 user_account.id，而 V2 用了
# RESTART IDENTITY，主键从 1 重新开始。残留的旧 token（loginId=1）会命中
# 清库后新注册的账号 1，等于串号。顺带清掉限流计数与管理员会话。
docker compose exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" FLUSHDB'

echo
echo "===== 5. 重建后端 ====="
# 新后端的 validateCoreSingleOption 扩到了 education / occupation，
# 必须配 V2 之后的字段定义才能保存档案，所以顺序是先迁移再重建。
docker compose up -d --build api
echo "等待 api 健康检查通过..."
for i in $(seq 1 60); do
  if curl -fsS -m 5 http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "api 已就绪（第 ${i} 次探测）"; break
  fi
  sleep 5
  [ "$i" = 60 ] && { echo "api 未就绪，看日志："; docker compose logs --tail=80 api; exit 1; }
done
curl -s http://127.0.0.1:8080/actuator/health; echo

echo
echo "===== 6. 校验字段定义已对齐规范 ====="
docker compose exec -T postgres psql -U archive_owner -d marriage_archive -c "
  SELECT field_code, data_type, required, options_json
  FROM profile_field_definition WHERE storage_kind = 'CORE' ORDER BY sort_order;"
echo "预期：education / occupation / gender / income_range 都是 SINGLE_OPTION 且带选项；"
echo "     city 仍是 TEXT（三级联动在前端，结果按「省 / 市 / 区」存文本）。"
docker compose exec -T postgres psql -U archive_owner -d marriage_archive \
  -c "SELECT count(*) AS should_be_zero FROM user_account;"

echo
echo "===== 7. 前端产物（只有 guest-app 变了，admin-web 本次未改动） ====="
if command -v npm >/dev/null 2>&1; then
  (cd apps/guest-app && npm ci --legacy-peer-deps && npm run build:h5)
  rsync -a --delete apps/guest-app/dist/build/h5/ /www/wwwroot/goldlove.xyz/
  # 宝塔给站点根目录放了 .user.ini 且加了 chattr +i，rsync --delete 删不掉，忽略即可
  chown -R www:www /www/wwwroot/goldlove.xyz 2>/dev/null || true
  nginx -t && nginx -s reload
else
  echo "服务器没有 npm，需要在本地 npm run build:h5 后把 dist/build/h5/ 同步到"
  echo "/www/wwwroot/goldlove.xyz/。跳过这一步。"
fi

echo
echo "===== 完成 ====="
cat <<'CHECKLIST'
自检（清库后没有任何用户账号，从注册开始走）：
  1) https://goldlove.xyz/#/pages/design/index  → 规范预览页，逐区块对照高保真图
  2) 用未注册手机号注册（12–128 位且含字母与数字）→ 直接进档案页
  3) 档案页逐项确认新控件：
     · 性别 = 三格单选，含「不公开」
     · 出生日期 = 底部滚轮，年 / 月 / 日三列
     · 学历、职业 = 下拉（职业可搜索），不再是自由文本输入框
     · 年薪 = 底部滚轮；选「保密」后出现锁标且不展示具体区间
     · 所在城市 = 省 / 市 / 区三列联动，共 34 个省级单位（含港澳台）
  4) 上传头像与生活照 → 保存 → 完整度进度条与分项清单更新
  5) https://admin.goldlove.xyz 用 operator 登录（V2 保留了 admin_user，密码没变）
     → 档案管理里能看到这份档案，字段值为新选项
  6) 后台生成绑定该手机号的激活码 → 嘉宾端「我的 → 会员」兑换 → 等级变 VIP
  7) 走一次支付宝 ¥0.01 → 回跳后等级变 VIP、后台订单列表出现该笔

回滚：代码 git reset --hard <上一个提交> 后 docker compose up -d --build api；
      但 V2 清掉的数据无法回滚（本次按决策未备份）。
CHECKLIST
