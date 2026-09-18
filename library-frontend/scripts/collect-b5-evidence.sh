#!/usr/bin/env bash
# B5 前端证据采集：在 library-frontend 下执行五项默认门禁并记录到证据目录。
set -uo pipefail
cd "$(dirname "$0")/.."

EVIDENCE_DIR="${1:-../artifacts/m5-b5-frontend-run1}"
mkdir -p "$EVIDENCE_DIR"

{
  echo '== environment =='
  node --version
  npm --version
  echo "branch: $(git -C .. rev-parse --abbrev-ref HEAD)"
  echo "head: $(git -C .. rev-parse HEAD)"
  echo
} > "$EVIDENCE_DIR/summary.txt"

: > "$EVIDENCE_DIR/commands.txt"
for cmd in 'npm run architecture:check' 'npm run contracts:check' 'npm run lint:check' 'npm run test:ci' 'npm run build'; do
  echo "\$ $cmd" >> "$EVIDENCE_DIR/commands.txt"
  eval "$cmd" > /tmp/mls-b0-cmd.log 2>&1
  code=$?
  echo "exit=$code" >> "$EVIDENCE_DIR/commands.txt"
  tail -8 /tmp/mls-b0-cmd.log >> "$EVIDENCE_DIR/commands.txt"
  echo >> "$EVIDENCE_DIR/commands.txt"
  if [ "$code" -ne 0 ]; then
    echo "gate failed: $cmd" >&2
    exit "$code"
  fi
done

cat > "$EVIDENCE_DIR/scenario-coverage.md" <<'EOF'
# B5 前端场景覆盖

- 14 个共同样例全部装载并通过外形校验（scenarios/index.test.js）。
- Mock Adapter 按场景回放匿名会话、Cookie 连续会话、错误 Cookie 不可见、登录不提升访客会话、访客共享额度、AI L2 预填与额度耗尽等批准样例（mockAdapter.test.js）。
- 未登记场景/夹具的操作立即失败（MOCK_MISSING_FIXTURE），mock 不实现业务规则。
- HTTP Adapter：same-origin Cookie 由浏览器管理且 JavaScript 不构造 Cookie 头；同时覆盖信封解析、Bearer、幂等键、错误分类、超时与取消（request.test.js）。
- 路由守卫：`/assistant` 匿名直达；其余受保护路由仍按未登录续行与角色权限守卫（guard.test.js）。
- AI 页面：呈现服务端 `VISITOR` 与访客共享额度，401/404 可见，登录不提升既有访客会话；L2 只预填现行表单，取消零业务请求。
EOF

echo "evidence written to $EVIDENCE_DIR"
