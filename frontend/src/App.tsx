import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'

type Credentials = { email: string; password: string }
type Verification = { email: string; code: string }

async function post<T>(path: string, body: T) {
  const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'include' })
  if (!csrfResponse.ok) throw new Error('The request could not be completed.')
  const { token } = (await csrfResponse.json()) as { token: string }
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error('The request could not be completed.')
  return response
}

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [challengeSent, setChallengeSent] = useState(false)
  const [signedIn, setSignedIn] = useState(false)

  const requestLogin = useMutation({
    mutationFn: (credentials: Credentials) =>
      post('/api/auth/login-request', credentials),
    onSuccess: () => setChallengeSent(true),
  })
  const verifyLogin = useMutation({
    mutationFn: (verification: Verification) =>
      post('/api/auth/login-verify', verification),
    onSuccess: () => setSignedIn(true),
  })
  const error = requestLogin.error ?? verifyLogin.error

  return (
    <main>
      <h1>Sign in</h1>
      {signedIn ? (
        <p role="status">Signed in successfully.</p>
      ) : challengeSent ? (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            verifyLogin.mutate({ email, code })
          }}
        >
          <label htmlFor="code">Email verification code</label>
          <input
            autoComplete="one-time-code"
            id="code"
            inputMode="numeric"
            maxLength={6}
            onChange={(event) => setCode(event.target.value)}
            pattern="[0-9]{6}"
            required
            value={code}
          />
          <button disabled={verifyLogin.isPending} type="submit">
            Verify
          </button>
        </form>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            requestLogin.mutate({ email, password })
          }}
        >
          <label htmlFor="email">Email</label>
          <input
            autoComplete="email"
            id="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />
          <label htmlFor="password">Password</label>
          <input
            autoComplete="current-password"
            id="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />
          <button disabled={requestLogin.isPending} type="submit">
            Continue
          </button>
        </form>
      )}
      {error && <p role="alert">{error.message}</p>}
    </main>
  )
}

export default function App() {
  return <LoginPage />
}
