#!/usr/bin/env bash
# scripts/sanitize-evidence-traces.sh —— Playwright trace 证据的销毁/脱敏/核验（第一控制）。
# 口径登记见 e2e/EVIDENCE-POLICY.md。
#
# 用法：
#   bash scripts/sanitize-evidence-traces.sh <test-results 目录>
#       销毁模式（默认）。删除目录内全部 trace.zip 与名为 trace 的目录，
#       逐条输出销毁清单。这是交付前的默认动作。
#   bash scripts/sanitize-evidence-traces.sh --keep-redacted <目录> [SECRET_FILE...]
#       脱敏模式。逐个解包 trace.zip，按内置动态凭据规则脱敏后原位重新打包，
#       保持 trace 内部结构与 JSON 行可读；写回前对解包树复核一遍，仍有命中
#       则不写回并以退出码 1 结束。SECRET_FILE 为可选的附加明文秘密值清单
#       （每行一个值，可由 scripts/collect-evidence-secret-values.sh 生成），
#       是内置规则的补充而非替代。
#   bash scripts/sanitize-evidence-traces.sh --verify <目录>
#       核验模式。对目录树逐文件做「若按内置规则脱敏是否会变化」探测，
#       任何文件会变化或二进制命中凭据模式即判为存在动态凭据模式并退出 1；
#       不修改任何文件。
#
# 内置动态凭据规则（与 .env 值无关，依据仓内实现锚定）：
#   - Cookie / Set-Cookie / (Proxy-)Authorization 头：Playwright trace 的
#     JSON name/value 对与纯文本行两种形态；
#   - Bearer 方案令牌、JWT（eyJ 开头三段式）、sk- 前缀 API key 形态；
#   - 会话令牌键值对形态：sessionid=、session_id=、token=、access_token=、
#     csrf_token= 等（覆盖 Cookie 对与 URL 查询串）；
#   - JSON 字符串字段键名含 token/secret/password/credential/api_key/
#     authorization/cookie 的值；
#   - 本系统生产会话令牌形态：32 位小写字母数字随机串
#     （library-server/arc/src/main/java/com/service/impl/TokenServiceImpl.java
#     的 getRandomString(32)，字符表见 com/utils/CommonUtil.java），按
#     \b[a-z0-9]{32}\b 匹配，宁可过度脱敏不放过漏网。
#
# 边界与限制（如实声明）：
#   - 第一控制是原始凭据不进长期证据：默认销毁；--keep-redacted 仅用于确需
#     保留结构可读 trace 的例外，且必须在拷入 artifacts/ 之前执行——销毁与
#     脱敏两种模式拒绝路径含 artifacts 段的目标，交付后材料不再就地改动；
#     --verify 为只读探测，允许指向 artifacts/ 下的交付树做核验。
#   - 二进制资源（截图、压缩体）不做就地脱敏：字节级替换会破坏其内部结构。
#     脱敏模式下二进制命中凭据模式即整体放弃写回并退出 1（改用默认销毁）；
#     核验模式对二进制只做高信号值形态子集扫描，属兜底而非完整证明。
#   - 脱敏按正则逐段替换，值内含转义引号的极端场景可能破坏该行 JSON 可解析
#     性；交付前应对保留的 trace 做人工抽查。
#   - test-results 里的 error-context.md、report.json 等非 trace 文件不属于
#     本脚本处理范围，由敏感证据扫描（scripts/check-sensitive-evidence*.sh）
#     兜底。
#   - 依赖 python3（本仓执行环境无 zip/unzip 命令，解包/重打包走 zipfile）。
set -Eeuo pipefail

usage() {
  echo "usage: $0 <test-results-dir>" >&2
  echo "       $0 --keep-redacted <dir> [SECRET_FILE...]" >&2
  echo "       $0 --verify <dir>" >&2
}

mode="destroy"
while (( $# )); do
  case "$1" in
    --keep-redacted) mode="redact"; shift ;;
    --verify) mode="verify"; shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; break ;;
    -*) echo "unknown option: $1" >&2; usage; exit 2 ;;
    *) break ;;
  esac
done

if (( $# < 1 )); then
  usage
  exit 2
fi
target="$1"
shift

command -v python3 >/dev/null 2>&1 || { echo 'python3 is not available; sanitizer cannot run' >&2; exit 2; }
test -d "$target" || { echo "not a directory: $target" >&2; exit 2; }

if [[ "$mode" == "destroy" || "$mode" == "redact" ]]; then
  case "$target" in
    artifacts|artifacts/*|*/artifacts|*/artifacts/*)
      echo "refusing to modify inside artifacts/ (delivered evidence stays immutable): $target" >&2
      exit 2 ;;
  esac
fi

workroot="$(mktemp -d "${TMPDIR:-/tmp}/sanitize-evidence-traces.XXXXXX")"
trap 'rm -rf -- "$workroot"' EXIT
engine="$workroot/engine.py"
cat > "$engine" <<'PY'
import os
import re
import sys
import zipfile

REDACTED = b"[REDACTED]"

# 内置动态凭据规则：字节级替换、幂等（对已脱敏输出不再产生变化）。
RULES = [
    ("header-json-pair",
     re.compile(rb'("name"\s*:\s*"(?:cookie|set-cookie|authorization|proxy-authorization)"\s*,\s*"value"\s*:\s*")([^"]*)(")', re.I),
     rb"\g<1>" + REDACTED + rb"\g<3>"),
    ("header-json-pair-rev",
     re.compile(rb'("value"\s*:\s*")([^"]*)("\s*,\s*"name"\s*:\s*"(?:cookie|set-cookie|authorization|proxy-authorization)")', re.I),
     rb"\g<1>" + REDACTED + rb"\g<3>"),
    ("header-line",
     re.compile(rb"(?im)^((?:cookie|set-cookie|authorization|proxy-authorization)[ \t]*:)[^\r\n]*"),
     rb"\g<1> " + REDACTED),
    ("bearer",
     re.compile(rb"(?i)(bearer[ \t]+)[A-Za-z0-9._~+/=-]+"),
     rb"\g<1>" + REDACTED),
    ("jwt",
     re.compile(rb"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"),
     b"[REDACTED_JWT]"),
    ("api-key",
     re.compile(rb"\bsk-[A-Za-z0-9_-]{16,}"),
     b"[REDACTED_API_KEY]"),
    ("token-pair",
     re.compile(rb"(?i)\b(session[_-]?id|sessionid|sid|session|auth[_-]?token|access[_-]?token|refresh[_-]?token|csrf[_-]?token|xsrf[_-]?token|api[_-]?key|apikey|token)([ \t]*=[ \t]*)[A-Za-z0-9._~+/=-]{8,}"),
     rb"\g<1>\g<2>" + REDACTED),
    ("json-cred-field",
     re.compile(rb'(?i)("[^"\r\n]*(?:token|secret|password|credential|api[_-]?key|authorization|cookie)[^"\r\n]*"[ \t]*:[ \t]*")([^"\r\n]*)(")'),
     rb"\g<1>" + REDACTED + rb"\g<3>"),
    ("session-token-shape",
     re.compile(rb"(?i)\b[a-z0-9]{32}\b"),
     b"[REDACTED_TOKEN]"),
]

# 二进制资源只做高信号值形态子集扫描（不脱敏、不写回）。
BINARY_PATTERNS = [
    re.compile(rb"(?i)bearer[ \t]+[A-Za-z0-9._~+/=-]{8,}"),
    re.compile(rb"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"),
    re.compile(rb"\bsk-[A-Za-z0-9_-]{16,}"),
    re.compile(rb"(?i)\b(?:set-cookie|authorization|proxy-authorization)[ \t]*:"),
    re.compile(rb"(?i)\b[a-z0-9]{32}\b"),
]


def load_secrets(paths):
    values = []
    for p in paths:
        with open(p, "rb") as fh:
            for line in fh.read().splitlines():
                line = line.strip()
                if len(line) >= 8:
                    values.append(line)
    return values


def is_binary(data):
    return b"\x00" in data[:8192]


def redact(data, secrets):
    for _, pat, rep in RULES:
        data = pat.sub(rep, data)
    for secret in secrets:
        data = data.replace(secret, b"[REDACTED_SECRET]")
    return data


def binary_hit(data):
    return any(pat.search(data) for pat in BINARY_PATTERNS)


def cmd_scan(argv):
    path = argv[0]
    write = False
    secret_paths = []
    for arg in argv[1:]:
        if arg == "--write":
            write = True
        elif not arg.startswith("-"):
            secret_paths.append(arg)
    secrets = load_secrets(secret_paths)
    with open(path, "rb") as fh:
        original = fh.read()
    if is_binary(original):
        print("binary-hit" if binary_hit(original) else "binary-clean", path)
        return 0
    redacted = redact(original, secrets)
    if redacted == original:
        print("same", path)
        return 0
    if write:
        with open(path, "wb") as fh:
            fh.write(redacted)
    print("changed", path)
    return 0


def cmd_extract(argv):
    zip_path, dest = argv[0], argv[1]
    os.makedirs(dest, exist_ok=True)
    dest_abs = os.path.abspath(dest)
    count = 0
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            if info.filename.endswith("/"):
                continue
            member = os.path.abspath(os.path.normpath(os.path.join(dest, info.filename)))
            if member != dest_abs and not member.startswith(dest_abs + os.sep):
                raise SystemExit(f"zip-slip blocked: {info.filename}")
            os.makedirs(os.path.dirname(member), exist_ok=True)
            with z.open(info) as src, open(member, "wb") as out:
                out.write(src.read())
            count += 1
    print(f"extracted {count} entries")
    return 0


def cmd_repack(argv):
    src, out = argv[0], argv[1]
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, dirs, files in os.walk(src):
            dirs.sort()
            for name in sorted(files):
                full = os.path.join(root, name)
                z.write(full, os.path.relpath(full, src))
                count += 1
    print(f"repacked {count} entries")
    return 0


def main():
    cmd, argv = sys.argv[1], sys.argv[2:]
    if cmd == "scan":
        return cmd_scan(argv)
    if cmd == "extract":
        return cmd_extract(argv)
    if cmd == "repack":
        return cmd_repack(argv)
    raise SystemExit(f"unknown engine command: {cmd}")


if __name__ == "__main__":
    raise SystemExit(main())
PY

if [[ "$mode" == "destroy" ]]; then
  destroyed=0
  while IFS= read -r -d '' p; do
    rm -rf -- "$p"
    printf 'destroyed: %s\n' "$p"
    destroyed=$((destroyed + 1))
  done < <(find "$target" \( -type f -name 'trace.zip' -o -type d -name 'trace' \) -print0 | LC_ALL=C sort -z)
  printf 'destroy mode: %d trace artifact(s) destroyed under %s\n' "$destroyed" "$target"
  exit 0
fi

if [[ "$mode" == "redact" ]]; then
  zips=()
  while IFS= read -r -d '' p; do zips+=("$p"); done < <(find "$target" -type f -name 'trace.zip' -print0 | LC_ALL=C sort -z)
  if (( ${#zips[@]} == 0 )); then
    echo "redact mode: no trace.zip under $target; nothing to do"
    exit 0
  fi
  rc_any=0
  idx=0
  for zip_path in "${zips[@]}"; do
    idx=$((idx + 1))
    bad=0
    work="$workroot/zip-$idx"
    python3 "$engine" extract "$zip_path" "$work/unpacked"
    bad=0
    # 第一遍：文本脱敏写回；二进制只检测，命中即放弃整个包。
    while IFS= read -r -d '' f; do
      out="$(python3 "$engine" scan "$f" --write "$@")"
      case "$out" in
        binary-hit*)
          printf 'credential pattern in binary resource (use destroy mode): %s\n' "${f#"$work"/}" >&2
          bad=1 ;;
        changed*) printf '  redacted: %s\n' "${f#"$work"/}" ;;
      esac
    done < <(find "$work/unpacked" -type f -print0 | LC_ALL=C sort -z)
    # 第二遍：复核解包树，仍有命中则不写回。
    while IFS= read -r -d '' f; do
      out="$(python3 "$engine" scan "$f" "$@")"
      case "$out" in
        changed*|binary-hit*)
          printf 'still dirty after redaction: %s\n' "${f#"$work"/}" >&2
          bad=1 ;;
      esac
    done < <(find "$work/unpacked" -type f -print0 | LC_ALL=C sort -z)
    if (( bad )); then
      echo "not repacking (residual credential patterns): $zip_path" >&2
      rc_any=1
    else
      python3 "$engine" repack "$work/unpacked" "$work/repacked.zip"
      mv -f -- "$work/repacked.zip" "$zip_path"
      printf 'repacked (redacted): %s\n' "$zip_path"
    fi
  done
  if (( rc_any )); then
    exit 1
  fi
  echo "redact mode: ${#zips[@]} trace.zip(s) redacted and repacked under $target"
  exit 0
fi

# 核验模式：逐文件探测，不修改任何文件。
rc=0
count=0
while IFS= read -r -d '' f; do
  count=$((count + 1))
  out="$(python3 "$engine" scan "$f")"
  case "$out" in
    changed*|binary-hit*)
      printf 'dynamic-credential pattern: %s\n' "$f"
      rc=1 ;;
  esac
done < <(find "$target" -type f -print0 | LC_ALL=C sort -z)
if (( rc )); then
  echo "verify: FAILED under $target (files listed above)" >&2
  exit 1
fi
echo "verify clean: $count file(s) would not change under the built-in redaction rules ($target)"
