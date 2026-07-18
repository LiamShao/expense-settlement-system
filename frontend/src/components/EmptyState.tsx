import { Box, Button, Typography } from '@mui/material'
import { Link } from 'react-router-dom'

interface EmptyStateProps {
  title: string
  description: string
  actionLabel?: string
  actionTo?: string
  onAction?: () => void
}

export function EmptyState({
  title,
  description,
  actionLabel,
  actionTo,
  onAction,
}: EmptyStateProps) {
  return (
    <Box sx={{ py: 7, px: 2, textAlign: 'center' }}>
      <Typography component="h2" variant="h2">
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ mt: 1, mb: 2 }}>
        {description}
      </Typography>
      {actionLabel &&
        (actionTo ? (
          <Button component={Link} to={actionTo} variant="contained">
            {actionLabel}
          </Button>
        ) : (
          <Button variant="outlined" onClick={onAction}>
            {actionLabel}
          </Button>
        ))}
    </Box>
  )
}
