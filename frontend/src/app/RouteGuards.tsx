import type { PropsWithChildren } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from '../features/auth/AuthContext'

export function ProtectedRoute({ children }: PropsWithChildren) {
  const { isAuthenticated, sessionMessage } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: `${location.pathname}${location.search}`,
          message: sessionMessage,
        }}
      />
    )
  }
  return children
}

interface RoleRouteProps extends PropsWithChildren {
  roles: Role[]
}

export function RoleRoute({ roles, children }: RoleRouteProps) {
  const { user } = useAuth()
  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/forbidden" replace />
  }
  return children
}
