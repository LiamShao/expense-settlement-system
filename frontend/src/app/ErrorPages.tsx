import { Button, Paper, Stack, Typography } from '@mui/material'
import { Link } from 'react-router-dom'

function ErrorPage({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <Paper variant="outlined" sx={{ p: 4, mt: 4, textAlign: 'center' }}>
      <Stack spacing={2} sx={{ alignItems: 'center' }}>
        <Typography component="h1" variant="h1">
          {title}
        </Typography>
        <Typography color="text.secondary">{description}</Typography>
        <Button component={Link} to="/expenses" variant="contained">
          申請一覧へ戻る
        </Button>
      </Stack>
    </Paper>
  )
}

export function ForbiddenPage() {
  return (
    <ErrorPage
      title="権限がありません"
      description="この画面を表示する権限がありません。"
    />
  )
}

export function NotFoundPage() {
  return (
    <ErrorPage
      title="ページが見つかりません"
      description="URLを確認するか、申請一覧から操作をやり直してください。"
    />
  )
}
