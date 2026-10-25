import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

export const server = setupServer(
  http.get('/api/auth/csrf', () => HttpResponse.json({ token: 'test-csrf-token' })),
  http.post('/api/auth/login-request', ({ request }) =>
    request.headers.get('x-xsrf-token') === 'test-csrf-token'
      ? HttpResponse.json({ status: 'OK' })
      : new HttpResponse(null, { status: 403 }),
  ),
  http.post('/api/auth/login-verify', ({ request }) =>
    request.headers.get('x-xsrf-token') === 'test-csrf-token'
      ? HttpResponse.json({ status: 'OK' })
      : new HttpResponse(null, { status: 403 }),
  ),
)
