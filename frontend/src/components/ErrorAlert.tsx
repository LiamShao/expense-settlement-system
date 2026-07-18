import { Alert, AlertTitle, Button, Stack } from '@mui/material'
import { ApiError } from '../api/client'

interface ErrorAlertProps {
  error: unknown
  onRetry?: () => void
}

export function ErrorAlert({ error, onRetry }: ErrorAlertProps) {
  const apiError = error instanceof ApiError ? error : null
  const message =
    error instanceof Error
      ? error.message
      : '予期しないエラーが発生しました。'

  return (
    <Alert
      severity={apiError?.status === 403 ? 'warning' : 'error'}
      action={
        onRetry ? (
          <Button color="inherit" size="small" onClick={onRetry}>
            再試行
          </Button>
        ) : undefined
      }
    >
      <AlertTitle>
        {apiError?.status === 403 ? '権限がありません' : '処理できませんでした'}
      </AlertTitle>
      <Stack spacing={0.5}>
        <span>{message}</span>
        {apiError?.details.map((detail) => (
          <span key={`${detail.field}-${detail.message}`}>
            {detail.field}: {detail.message}
          </span>
        ))}
      </Stack>
    </Alert>
  )
}
