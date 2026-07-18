import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { RouterProvider, type RouterProviderProps } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'
import { createAppRouter } from './app/router'
import { appTheme } from './app/theme'

interface AppProps {
  router?: RouterProviderProps['router']
}

export default function App({ router: providedRouter }: AppProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            staleTime: 30_000,
          },
          mutations: {
            retry: false,
          },
        },
      }),
  )
  const [router] = useState(() => providedRouter ?? createAppRouter())

  return (
    <ThemeProvider theme={appTheme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
