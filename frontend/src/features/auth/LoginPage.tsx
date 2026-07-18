import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import {
  Alert,
  Box,
  Button,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from './AuthContext'

const loginSchema = z.object({
  email: z
    .email('正しいメールアドレスを入力してください。')
    .max(255, 'メールアドレスは255文字以内で入力してください。'),
  password: z
    .string()
    .min(1, 'パスワードを入力してください。')
    .max(100, 'パスワードは100文字以内で入力してください。'),
})

type LoginValues = z.infer<typeof loginSchema>

interface LoginLocationState {
  from?: string
  message?: string
}

export function LoginPage() {
  const [showPassword, setShowPassword] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [loginInProgress, setLoginInProgress] = useState(false)
  const { isAuthenticated, login, sessionMessage } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LoginLocationState
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  if (isAuthenticated && !loginInProgress) {
    return <Navigate to="/expenses" replace />
  }

  const onSubmit = async (values: LoginValues) => {
    setSubmitError(null)
    setLoginInProgress(true)
    try {
      await login(values)
      const destination =
        state.from && state.from.startsWith('/') ? state.from : '/expenses'
      navigate(destination, { replace: true })
    } catch (error) {
      setLoginInProgress(false)
      setSubmitError(
        error instanceof Error
          ? error.message
          : 'ログインできませんでした。入力内容を確認してください。',
      )
    }
  }

  return (
    <Box
      component="main"
      sx={{
        minHeight: '100vh',
        bgcolor: 'background.default',
        p: 2,
        display: 'grid',
        placeItems: 'center',
      }}
    >
      <Box sx={{ width: '100%', maxWidth: 420 }}>
        <Typography
          component="h1"
          variant="h1"
          sx={{ mb: 3, textAlign: 'center' }}
        >
          経費精算システム
        </Typography>
        <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 } }}>
          <Typography component="h2" variant="h2">
            ログイン
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
            業務アカウントでログインしてください。
          </Typography>
          <Stack
            component="form"
            spacing={2}
            noValidate
            onSubmit={handleSubmit(onSubmit)}
          >
            {(submitError || state.message || sessionMessage) && (
              <Alert severity="error">
                {submitError ?? state.message ?? sessionMessage}
              </Alert>
            )}
            <TextField
              label="メールアドレス"
              type="email"
              autoComplete="username"
              fullWidth
              error={Boolean(errors.email)}
              helperText={errors.email?.message}
              {...register('email')}
            />
            <TextField
              label="パスワード"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              fullWidth
              error={Boolean(errors.password)}
              helperText={errors.password?.message}
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label={
                          showPassword
                            ? 'パスワードを非表示にする'
                            : 'パスワードを表示する'
                        }
                        edge="end"
                        onClick={() => setShowPassword((visible) => !visible)}
                      >
                        {showPassword ? (
                          <VisibilityOffIcon />
                        ) : (
                          <VisibilityIcon />
                        )}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
              {...register('password')}
            />
            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isSubmitting || loginInProgress}
            >
              {isSubmitting || loginInProgress ? 'ログイン中…' : 'ログインする'}
            </Button>
          </Stack>
        </Paper>
      </Box>
    </Box>
  )
}
