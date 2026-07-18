import {
  Box,
  Breadcrumbs,
  Link as MuiLink,
  Stack,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface BreadcrumbItem {
  label: string
  to?: string
}

interface PageHeaderProps {
  title: string
  description?: string
  breadcrumbs?: BreadcrumbItem[]
  action?: ReactNode
}

export function PageHeader({
  title,
  description,
  breadcrumbs,
  action,
}: PageHeaderProps) {
  return (
    <Box>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs aria-label="パンくずリスト" sx={{ mb: 1 }}>
          {breadcrumbs.map((item) =>
            item.to ? (
              <MuiLink component={Link} to={item.to} key={item.label}>
                {item.label}
              </MuiLink>
            ) : (
              <Typography color="text.secondary" key={item.label}>
                {item.label}
              </Typography>
            ),
          )}
        </Breadcrumbs>
      )}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{
          alignItems: { xs: 'stretch', sm: 'flex-start' },
          justifyContent: 'space-between',
        }}
      >
        <Box>
          <Typography component="h1" variant="h1">
            {title}
          </Typography>
          {description && (
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>
              {description}
            </Typography>
          )}
        </Box>
        {action && <Stack direction="row">{action}</Stack>}
      </Stack>
    </Box>
  )
}
