#!/usr/bin/env bash
#
# 一个命令启动 RecallTree 最小可运行服务（对应 Issue #3 验收标准第 1 条）。
#
#   ./scripts/dev-up.sh
#
# 依次完成：准备 .env -> 启动 PostgreSQL+pgvector 并等待就绪 -> 构建后端 -> 启动 API。
# 任何一步失败即以非零状态退出。

set -euo pipefail

# 无论从哪个目录调用，都以仓库根目录为工作目录。
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

log() { printf '\033[1;34m==>\033[0m %s\n' "$1"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$1" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 未安装。$2"
}

log "检查工具链"
require_cmd java "需要 JDK 21 或更高版本。"
require_cmd docker "需要 Docker Desktop 或兼容的容器运行时。"

java_major="$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [[ ! "$java_major" =~ ^[0-9]+$ ]] || (( java_major < 21 )); then
  fail "检测到 Java $java_major，RecallTree 需要 21 或更高版本（见 ADR-001）。"
fi

# Compose V2 是 docker 的子命令；旧的独立 docker-compose 已不受支持。
docker compose version >/dev/null 2>&1 \
  || fail "未检测到 Docker Compose V2。请升级 Docker 后重试。"

docker info >/dev/null 2>&1 \
  || fail "Docker 守护进程未运行。请先启动 Docker 后重试。"

if [[ ! -f .env ]]; then
  log "未发现 .env，从 .env.example 生成（其中均为本地开发默认值）"
  cp .env.example .env
fi

# 供本脚本自身读取端口等变量；docker compose 会独立加载同一份 .env。
set -a
# shellcheck disable=SC1091
source .env
set +a

db_port="${RECALLTREE_DB_PORT:-5432}"
db_user="${RECALLTREE_DB_USER:-recalltree}"
db_name="${RECALLTREE_DB_NAME:-recalltree}"
server_port="${RECALLTREE_SERVER_PORT:-8080}"

log "启动 PostgreSQL 17 + pgvector（宿主端口 $db_port）"
docker compose up -d postgres

log "等待数据库就绪"
# 依据 compose healthcheck 判定，而非固定 sleep，避免慢机器上误判失败。
deadline=$((SECONDS + 120))
while true; do
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    recalltree-postgres 2>/dev/null || echo missing)"
  case "$health" in
    healthy) break ;;
    missing|none)
      fail "容器 recalltree-postgres 不存在或未定义健康检查。请运行 'docker compose logs postgres' 排查。" ;;
  esac
  if (( SECONDS >= deadline )); then
    docker compose logs --tail 30 postgres || true
    fail "数据库在 120 秒内未就绪。"
  fi
  sleep 2
done
log "数据库就绪：$db_user@localhost:$db_port/$db_name"

log "构建后端（含单元测试与 ArchUnit 架构测试）"
./mvnw -q clean verify

boot_jar="$(find api/target -maxdepth 1 -name 'recalltree-api-*-boot.jar' -print -quit)"
[[ -n "$boot_jar" ]] || fail "未找到可执行 jar。请检查 api 模块的 spring-boot-maven-plugin 配置。"

log "启动 API：http://localhost:$server_port/v1/health"
log "按 Ctrl-C 停止；数据库容器会继续运行，用 'docker compose down' 停止。"
exec java -jar "$boot_jar"
