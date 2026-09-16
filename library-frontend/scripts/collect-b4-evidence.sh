#!/usr/bin/env bash
# B0 前端证据采集：在 library-frontend 下执行五项默认门禁并记录到证据目录。
set -uo pipefail
cd "$(dirname "$0")/.."

EVIDENCE_DIR="${1:-../artifacts/m5-b4-frontend-run1}"
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
  tail -5 /tmp/mls-b0-cmd.log >> "$EVIDENCE_DIR/commands.txt"
  echo >> "$EVIDENCE_DIR/commands.txt"
  if [ "$code" -ne 0 ]; then
    echo "gate failed: $cmd" >&2
    exit "$code"
  fi
done

cat > "$EVIDENCE_DIR/scenario-coverage.md" <<'EOF'
# B0 前端场景覆盖

- 9 个共同样例全部装载并通过外形校验（scenarios/index.test.js）。
- Mock Adapter 按场景回放：CATALOG-PUBLIC-SEARCH 成功样例、ORDER-CONCURRENT-CONFLICT 409、AI-QUOTA-EXHAUSTED 429、RETURN-WITH-FINE 固定回放（mockAdapter.test.js）。
- 未登记场景/夹具的操作立即失败（MOCK_MISSING_FIXTURE），mock 不实现业务规则。
- HTTP Adapter：信封解析、Bearer 头、写操作 Idempotency-Key 自动注入、409/401/500/网络/超时/取消分类、2xx 非成功码拒绝（request.test.js）。
- 路由守卫：未登录保存续行意图跳登录、角色不足 403、授权放行（guard.test.js）。
EOF

echo "evidence written to $EVIDENCE_DIR"
