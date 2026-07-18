import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { requestApi, type ApiRequestOptions } from '../../api/client'
import type { AuthResponse, User } from '../../api/types'

interface LoginInput {
  email: string
  password: string
}

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  sessionMessage: string | null
  login: (input: LoginInput) => Promise<void>
  logout: (message?: string) => void
  request: <T>(path: string, options?: RequestInit) => Promise<T>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function createBasicCredential(email: string, password: string): string {
  const bytes = new TextEncoder().encode(`${email}:${password}`)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return `Basic ${window.btoa(binary)}`
}

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [credential, setCredential] = useState<string | null>(null)
  const [sessionMessage, setSessionMessage] = useState<string | null>(null)

  const logout = useCallback(
    (message?: string) => {
      setUser(null)
      setCredential(null)
      setSessionMessage(message ?? null)
      queryClient.clear()
    },
    [queryClient],
  )

  const login = useCallback(async ({ email, password }: LoginInput) => {
    await requestApi<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })

    const nextCredential = createBasicCredential(email, password)
    const currentUser = await requestApi<User>('/api/auth/me', {
      credential: nextCredential,
    })

    setCredential(nextCredential)
    setUser(currentUser)
    setSessionMessage(null)
  }, [])

  const request = useCallback(
    <T,>(path: string, options: RequestInit = {}) => {
      if (!credential) {
        return Promise.reject(
          new Error('認証情報がありません。もう一度ログインしてください。'),
        )
      }
      const apiOptions: ApiRequestOptions = {
        ...options,
        credential,
        onUnauthorized: () =>
          logout('認証の有効期限が切れました。もう一度ログインしてください。'),
      }
      return requestApi<T>(path, apiOptions)
    },
    [credential, logout],
  )

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: Boolean(user && credential),
      sessionMessage,
      login,
      logout,
      request,
    }),
    [credential, login, logout, request, sessionMessage, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// A colocated hook keeps the context API private to this module.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
