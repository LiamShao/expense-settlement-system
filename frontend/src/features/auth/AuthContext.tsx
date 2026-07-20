import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  clearCsrfToken,
  requestApi,
  type ApiRequestOptions,
} from '../../api/client'
import type { AuthResponse, User } from '../../api/types'

interface LoginInput {
  email: string
  password: string
}

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  isInitializing: boolean
  sessionMessage: string | null
  login: (input: LoginInput) => Promise<void>
  logout: () => Promise<void>
  request: <T>(path: string, options?: RequestInit) => Promise<T>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [isInitializing, setIsInitializing] = useState(true)
  const [sessionMessage, setSessionMessage] = useState<string | null>(null)

  const clearSession = useCallback(
    (message?: string) => {
      setUser(null)
      setSessionMessage(message ?? null)
      clearCsrfToken()
      queryClient.clear()
    },
    [queryClient],
  )

  useEffect(() => {
    let active = true

    requestApi<User>('/api/auth/me')
      .then((currentUser) => {
        if (!active) {
          return
        }
        setUser(currentUser)
        setSessionMessage(null)
      })
      .catch((error: unknown) => {
        if (!active) {
          return
        }
        setUser(null)
        if (!(error instanceof ApiError && error.status === 401)) {
          setSessionMessage(
            error instanceof Error
              ? error.message
              : '認証状態を確認できませんでした。',
          )
        }
      })
      .finally(() => {
        if (active) {
          setIsInitializing(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async ({ email, password }: LoginInput) => {
    const response = await requestApi<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setUser(response.user)
    setSessionMessage(null)
  }, [])

  const logout = useCallback(async () => {
    await requestApi<null>('/api/auth/logout', { method: 'POST' })
    clearSession()
  }, [clearSession])

  const request = useCallback(
    <T,>(path: string, options: RequestInit = {}) => {
      if (!user) {
        return Promise.reject(
          new Error('認証セッションがありません。もう一度ログインしてください。'),
        )
      }
      const apiOptions: ApiRequestOptions = {
        ...options,
        onUnauthorized: () =>
          clearSession(
            '認証の有効期限が切れました。もう一度ログインしてください。',
          ),
      }
      return requestApi<T>(path, apiOptions)
    },
    [clearSession, user],
  )

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: Boolean(user),
      isInitializing,
      sessionMessage,
      login,
      logout,
      request,
    }),
    [isInitializing, login, logout, request, sessionMessage, user],
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
