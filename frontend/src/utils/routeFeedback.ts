import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

interface RouteFeedbackState {
  successMessage?: string
}

export function useRouteSuccessMessage() {
  const location = useLocation()
  const navigate = useNavigate()
  const routeState = (location.state ?? {}) as RouteFeedbackState
  const [message, setMessage] = useState<string | null>(
    routeState.successMessage ?? null,
  )

  useEffect(() => {
    if (routeState.successMessage) {
      navigate(`${location.pathname}${location.search}`, {
        replace: true,
        state: null,
      })
    }
  }, [
    location.pathname,
    location.search,
    navigate,
    routeState.successMessage,
  ])

  return [message, setMessage] as const
}
