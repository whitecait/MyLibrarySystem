#!/usr/bin/env node
import { writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

function check(value, message) { if (!value) throw new Error(message); }
export function validateToolCall(json) {
  const calls = json.choices?.[0]?.message?.tool_calls;
  check(Array.isArray(calls) && calls.length > 0, 'missing tool_calls');
  for (const call of calls) {
    check(typeof call.id === 'string' && call.id.length > 0 && call.type === 'function', 'invalid tool call identity');
    check(call.function?.name === 'search_books', 'unexpected function');
    check(typeof call.function.arguments === 'string', 'arguments must be JSON text');
    const args = JSON.parse(call.function.arguments);
    check(args && typeof args.keyword === 'string' && args.keyword.trim().length > 0, 'missing keyword');
  }
}

export async function verify({ base, key, model, timeoutMs = 15000, thinkingMode, fetchImpl = fetch }) {
  const url = new URL(base);
  check(['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash,
    'BASE_URL must be an HTTP(S) URL without credentials, query or fragment');
  check(key && model, 'API_KEY and MODEL are required');
  check(thinkingMode === undefined || ['enabled','disabled'].includes(thinkingMode), 'invalid thinking mode');
  const endpoint = url.href.replace(/\/$/, '');
  const results = [];
  const headers = { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' };
  async function request(path, options = {}) {
    // Deadline also covers response-body consumption; no raw provider body is logged.
    const response = await fetchImpl(`${endpoint}/${path}`, { ...options, signal: AbortSignal.timeout(timeoutMs), redirect: 'error' });
    let json;
    if (response.ok) json = await response.json();
    else await response.body?.cancel();
    return { status: response.status, json };
  }
  const chat = (body, h = headers) => request('chat/completions', { method: 'POST', headers: h, body: JSON.stringify(body) });
  const payload = { model, ...(thinkingMode ? {thinking:{type:thinkingMode}} : {}), messages: [{ role: 'user', content: 'ping' }], max_tokens: 16 };
  async function scenario(id, run) {
    try { await run(); results.push({ id, status: 'PASS' }); }
    catch (error) { results.push({ id, status: 'FAIL', reason: error.name === 'TimeoutError' ? 'request deadline exceeded' : 'contract assertion or transport failed' }); }
  }
  await scenario('S1 models', async () => {
    const r = await request('models', { headers });
    check(r.status === 200 && Array.isArray(r.json?.data), 'models response');
    check(r.json.data.some(x => x.id === model), 'configured model absent');
  });
  await scenario('S2 completion and usage', async () => {
    const r = await chat(payload);
    check(r.status === 200 && r.json?.choices?.length > 0, 'completion');
    check(Number.isInteger(r.json.usage?.total_tokens) && r.json.usage.total_tokens >= 0, 'usage');
  });
  await scenario('S3 required tool call', async () => {
    const r = await chat({ ...payload, max_tokens: 200,
      messages: [{ role: 'user', content: '查询书目重构' }],
      tools: [{ type: 'function', function: { name: 'search_books', description: '检索书目',
        parameters: { type: 'object', properties: { keyword: { type: 'string' } }, required: ['keyword'] } } }],
      tool_choice: { type: 'function', function: { name: 'search_books' } } });
    check(r.status === 200, 'tool response'); validateToolCall(r.json);
  });
  await scenario('S4 invalid model', async () => {
    const r = await chat({ ...payload, model: 'definitely-not-a-model' });
    check([400, 404, 422].includes(r.status), 'model error must not be auth/rate limit');
  });
  await scenario('S5 missing credentials', async () => {
    const r = await chat(payload, { 'Content-Type': 'application/json' });
    check([401, 403].includes(r.status), 'authentication');
  });
  await scenario('S6 client cancellation before dispatch', async () => {
    const controller = new AbortController(); controller.abort();
    let aborted = false;
    try { await fetchImpl(`${endpoint}/chat/completions`, { method: 'POST', headers,
      body: JSON.stringify(payload), signal: controller.signal, redirect: 'error' }); }
    catch (error) { aborted = error.name === 'AbortError'; }
    check(aborted, 'cancellation not observed');
  });
  return { model, thinkingMode: thinkingMode ?? 'provider-default', endpoint: url.origin + url.pathname, results, passed: results.every(x => x.status === 'PASS') };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.env.BASE_URL || !process.env.API_KEY || !process.env.MODEL) {
    console.error('请在本地环境设置 BASE_URL、API_KEY、MODEL；不在聊天或命令行参数填写密钥。');
    process.exitCode = 2;
  } else {
    try {
      const report = await verify({ base: process.env.BASE_URL, key: process.env.API_KEY, model: process.env.MODEL, thinkingMode: process.env.THINKING_MODE });
      const stamp = new Date().toISOString().replaceAll(':', '-');
      writeFileSync(new URL(`run-${stamp}.json`, import.meta.url), JSON.stringify({ timestamp: stamp, scope: 'transport contract; S6 only client cancellation, not server timeout or business acceptance', ...report }, null, 2));
      console.log(JSON.stringify(report)); process.exitCode = report.passed ? 0 : 1;
    } catch { console.error('配置或执行失败；未记录密钥、响应正文或原始错误。'); process.exitCode = 2; }
  }
}
