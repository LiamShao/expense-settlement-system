import { Paper, Skeleton, Stack } from '@mui/material'

export function LoadingSkeleton() {
  return (
    <Paper variant="outlined" sx={{ p: 3 }} aria-label="読み込み中">
      <Stack spacing={2}>
        <Skeleton variant="text" width="35%" height={32} />
        <Skeleton variant="rounded" height={48} />
        <Skeleton variant="rounded" height={48} />
        <Skeleton variant="rounded" height={48} />
      </Stack>
    </Paper>
  )
}
