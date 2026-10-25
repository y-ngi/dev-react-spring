import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

export const server = setupServer(
  http.post('/api/auth/login-request', () => HttpResponse.json({ status: 'OK' })),
  http.post('/api/auth/login-verify', () => HttpResponse.json({ status: 'OK' })),
)
