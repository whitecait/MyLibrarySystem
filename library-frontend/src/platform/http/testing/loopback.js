// Test-only loopback receiver; requests still use the generated operations and real HTTP adapter.
import { createServer } from 'node:http'
import { createHttpTransport } from '../adapter.js'

export async function startLoopbackHttp({ responseEnvelope, getToken }) {
  const requests = []
  const server = createServer(async (request, response) => {
    let body = ''
    for await (const chunk of request) body += chunk
    requests.push({
      method: request.method, path: request.url, headers: request.headers,
      body: body ? JSON.parse(body) : null,
    })
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(responseEnvelope))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const http = createHttpTransport({ getToken })
  return {
    requests,
    transport: {
      mode: 'http',
      request(operationId, options) {
        return http.request(operationId, {
          ...options,
          fetchImpl: (url, init) => fetch(new URL(url, origin), init),
        })
      },
    },
    close: () => new Promise((resolve) => server.close(resolve)),
  }
}
