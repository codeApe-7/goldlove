#!/usr/bin/env bash
# 部署 feat/simplify-free-registration-vip 并重新初始化数据库。
# 在服务器上执行（宝塔面板 → 终端，或 ssh root@8.218.225.226）。
#
# ⚠️ 第 4 步会删除 postgres 与 redis 数据卷，线上现有账号/档案/订单全部消失。
#    这是本次重构的既定决策（清库重置），但请确认第 1 步的备份文件真的生成了再往下走。
set -euo pipefail

cd /opt/love

echo "===== 1. 备份现有数据库（清库前的最后一道保险） ====="
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="/root/marriage_archive-before-reset-${STAMP}.sql"
docker compose exec -T postgres pg_dump -U archive_owner -d marriage_archive > "$BACKUP"
ls -lh "$BACKUP"
test -s "$BACKUP" || { echo "备份为空，中止"; exit 1; }

echo
echo "===== 2. 拉取新代码 ====="
git fetch origin
# compose.yaml 若被本机改过，以仓库版本为准；compose.override.yaml 是未跟踪文件，不会被动
git checkout -f feat/simplify-free-registration-vip 2>/dev/null \
  || git checkout -B feat/simplify-free-registration-vip origin/feat/simplify-free-registration-vip
git reset --hard origin/feat/simplify-free-registration-vip
git log --oneline -1

echo
echo "===== 3. 更新 .env ====="
cp .env ".env.bak-${STAMP}"

# 就地改写/追加，不动其他键
set_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    # 用 | 作分隔符，值里含 / 不会打断 sed
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

# 渠道网关：源码里原本硬编码的默认值，现在改成必填，缺了渠道就不装配
set_env XPAY_BASE_URL 'https://xpay.unbb.cn/xpay/epayn'
# 回调路径变了：/public/online-payments/notifications/xpay → /public/payment-notifications/xpay
# 同时从 ceshi.kkhyj.xyz 收回到本站，异步通知才能真正到达
set_env XPAY_NOTIFY_URL 'https://api.goldlove.xyz/api/v1/public/payment-notifications/xpay'
# 回跳落到会员页（H5 是 hash 路由）
set_env XPAY_RETURN_URL 'https://goldlove.xyz/#/pages/vip/index'
# 建档费改成 VIP 升级费，沿用原来的 1 分钱联调值
set_env VIP_UPGRADE_AMOUNT_MINOR '1'

# 这些键对应的功能已随重构删除，留着无害但会误导，注释掉
sed -i -E 's|^(PHONE_ENCRYPTION_KEY|PHONE_SEARCH_KEY|PROFILE_ENCRYPTION_KEY|PROFILE_HMAC_KEY|WECHAT_[A-Z_]+|ONLINE_REGISTRATION_AMOUNT_MINOR|REGISTRATION_TOKEN_TTL)=|# [removed by refactor] \1=|' .env

chmod 600 .env
echo "--- 支付相关键（不含私钥） ---"
grep -E '^(XPAY_(BASE_URL|NOTIFY_URL|RETURN_URL|PID)|ONLINE_PAYMENT_PROVIDER|VIP_UPGRADE_AMOUNT_MINOR|MEMBERSHIP_SVIP_THRESHOLD_MINOR)=' .env

echo
echo "===== 4. 停服并清空数据卷 ====="
docker compose down -v

echo
echo "===== 5. 重建并启动（会跑角色初始化脚本 + 全新 V1 迁移） ====="
docker compose up -d --build
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
echo "===== 6. 校验新库结构 ====="
docker compose exec -T postgres psql -U archive_owner -d marriage_archive -c '\dt'
docker compose exec -T postgres psql -U archive_owner -d marriage_archive \
  -c "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec -T postgres psql -U archive_owner -d marriage_archive \
  -c "SELECT count(*) AS core_fields FROM profile_field_definition WHERE storage_kind='CORE';"

echo
echo "===== 7. 重建管理员（清库后 admin_user 是空的） ====="
echo "把 .env 里的 ADMIN_BOOTSTRAP_ENABLED 临时打开，起一次 api，再关回去。"
echo "⚠️ 顺手把 ADMIN_BOOTSTRAP_PASSWORD 改成一个新的强密码——"
echo "   原来的 Admin@2026local 是初始默认值，而后台是公网可达的。"
read -r -p "现在手动编辑 .env（另开一个终端 vi /opt/love/.env），改好后按回车继续：" _
grep -E '^ADMIN_BOOTSTRAP_(ENABLED|USERNAME)=' .env
docker compose up -d api
sleep 20
docker compose logs --tail=30 api | grep -iE "bootstrap|admin" || true
docker compose exec -T postgres psql -U archive_owner -d marriage_archive \
  -c "SELECT id, username, status FROM admin_user;"
echo
echo "确认管理员已创建后，把 ADMIN_BOOTSTRAP_ENABLED 改回 false，然后："
echo "  docker compose up -d api"

echo
echo "===== 8. 前端产物 ====="
if command -v npm >/dev/null 2>&1; then
  (cd apps/guest-app && npm ci --legacy-peer-deps && npm run build:h5)
  rsync -a --delete apps/guest-app/dist/build/h5/ /www/wwwroot/goldlove.xyz/
  (cd apps/admin-web && npm ci && npm run build)
  rsync -a --delete apps/admin-web/dist/ /www/wwwroot/admin.goldlove.xyz/
  # 宝塔给站点根目录放了 .user.ini 且加了 chattr +i，rsync --delete 删不掉它，忽略即可
  chown -R www:www /www/wwwroot/goldlove.xyz /www/wwwroot/admin.goldlove.xyz 2>/dev/null || true
  nginx -t && nginx -s reload
else
  echo "服务器没有 npm，前端产物需要在本地构建后同步。跳过这一步。"
fi

echo
echo "===== 完成 ====="
echo "数据库备份： $BACKUP"
echo ".env 备份：  /opt/love/.env.bak-${STAMP}"
echo
echo "自检："
echo "  1) https://goldlove.xyz            → 登录页，应能看到「还没有账号？免费注册建档」"
echo "  2) 用未注册手机号注册 → 直接进档案页 → 填资料保存"
echo "  3) https://admin.goldlove.xyz      → 档案管理里应能看到这份档案"
echo "  4) 后台生成一个绑定该手机号的激活码 → 嘉宾端「我的 → 会员」兑换 → 等级变 VIP"
echo "  5) 走一次支付宝支付 ¥0.01 → 回跳后等级变 VIP、后台订单列表出现该笔"
