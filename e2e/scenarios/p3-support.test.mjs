import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sendRealAiMessage, walkInDraftFrom } from './p3-support.mjs'

function messageResult(actions) {
  const data = { actions }
  return { status: 200, envelope: { code: 'MLS-AI-S001', data }, data }
}

test('librarian draft selection consumes the wrapped message result without a second model call', async () => {
  const draft = {
    actionType: 'WALK_IN_LOAN', level: 'L2', draftId: '7217',
    prefill: { readerId: '2', titleId: '700001', copyId: '800002' },
  }
  const page = messagePage(200, messageResult([draft]).envelope)
  const result = await sendRealAiMessage(page, 'generate the fixture draft')
  assert.equal(walkInDraftFrom(result), draft)
  assert.equal(page.message, 'generate the fixture draft')
  assert.equal(page.clicks, 1)
})

test('guidance and unrelated L2 actions do not count as a walk-in draft', () => {
  assert.equal(walkInDraftFrom(messageResult([
    { actionType: 'WALK_IN_LOAN', level: 'L3' },
    { actionType: 'RENEW_LOAN', level: 'L2' },
  ])), undefined)
})

test('a valid response without actions allows the second prompt', () => {
  assert.equal(walkInDraftFrom(messageResult([])), undefined)
})

function messagePage(status, envelope) {
  const response = {
    url: () => 'http://p3.test/api/v1/ai/sessions/7001/messages',
    request: () => ({ method: () => 'POST' }),
    status: () => status,
    json: async () => envelope,
  }
  return {
    message: '', clicks: 0,
    waitForResponse(predicate) {
      assert.equal(predicate(response), true)
      return Promise.resolve(response)
    },
    getByTestId(id) {
      return {
        fill: async (value) => { assert.equal(id, 'ai-input'); this.message = value },
        click: async () => { assert.equal(id, 'ai-send'); this.clicks += 1 },
      }
    },
  }
}

test('a malformed successful AI response fails instead of exhausting both prompts', async () => {
  await assert.rejects(sendRealAiMessage(messagePage(200, { data: {} }), 'draft'), /actions list/)
})

test('the reader denial option accepts only the documented tool denial', async () => {
  const result = await sendRealAiMessage(messagePage(422, { code: 'MLS-AI-004' }), 'draft', { allowRoleDenied: true })
  assert.equal(result.status, 422)
  assert.equal(result.envelope.code, 'MLS-AI-004')
})

test('model and authentication failures cannot masquerade as reader denial', async () => {
  for (const [status, code] of [[500, 'MLS-AI-004'], [401, 'MLS-ID-001'], [422, 'MLS-AI-003']]) {
    await assert.rejects(sendRealAiMessage(messagePage(status, { code }), 'draft', { allowRoleDenied: true }))
  }
})

test('the librarian path must reject a tool denial', async () => {
  await assert.rejects(sendRealAiMessage(messagePage(422, { code: 'MLS-AI-004' }), 'draft'))
})
