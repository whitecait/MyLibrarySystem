import test from 'node:test';
import assert from 'node:assert/strict';
import { verify } from './verify-endpoint.mjs';
const options = { base: 'https://fixture.invalid/v1', key: 'not-a-secret', model: 'fixture' };
function transport({ tools = true, abort = true, invalidStatus = 404 } = {}) {
  return async (url, init) => {
    if (init.signal.aborted && abort) throw new DOMException('aborted', 'AbortError');
    if (!init.headers.Authorization) return new Response('{}', {status:401});
    const body = JSON.parse(init.body || '{}');
    if (body.model === 'definitely-not-a-model') return new Response('{}', {status:invalidStatus});
    if (url.endsWith('/models')) return Response.json({data:[{id:'fixture'}]});
    const message = body.tools && tools ? {tool_calls:[{id:'call1',type:'function',function:{name:'search_books',arguments:'{"keyword":"重构"}'}}]} : {content:'no tools'};
    return Response.json({choices:[{message}],usage:{total_tokens:1}});
  };
}
test('valid contract passes all scenarios', async () => assert.equal((await verify({...options,fetchImpl:transport()})).passed,true));
test('text-only response cannot pass tool calling', async () => {
  const r = await verify({...options,fetchImpl:transport({tools:false})});
  assert.equal(r.results[2].status,'FAIL'); assert.equal(r.passed,false);
});
test('unobserved cancellation fails', async () => assert.equal((await verify({...options,fetchImpl:transport({abort:false})})).results[5].status,'FAIL'));
test('rate limit cannot prove invalid model rejection', async () => assert.equal((await verify({...options,fetchImpl:transport({invalidStatus:429})})).results[3].status,'FAIL'));
test('credential-bearing URL rejected', async () => assert.rejects(verify({...options,base:'https://user:secret@fixture.invalid/v1',fetchImpl:transport()})));

test('explicit disabled profile is sent on every chat request', async () => {
  const base = transport();let seen=0;
  const fetchImpl=async(url,options)=>{if(options.body){assert.equal(JSON.parse(options.body).thinking.type,'disabled');seen++;}return base(url,options);};
  const r=await verify({...options,thinkingMode:'disabled',fetchImpl});
  assert.equal(r.passed,true);assert.equal(r.thinkingMode,'disabled');assert.equal(seen,5);
});
test('invalid profile rejected before transport', async () => assert.rejects(verify({...options,thinkingMode:'guess',fetchImpl:transport()})));
