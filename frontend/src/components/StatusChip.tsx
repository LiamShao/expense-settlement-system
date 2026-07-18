import { Chip } from '@mui/material'
import type { ExpenseStatus } from '../api/types'

const statusStyles: Record<
  ExpenseStatus,
  { color: string; backgroundColor: string; fallbackLabel: string }
> = {
  DRAFT: {
    color: '#475569',
    backgroundColor: '#EEF2F6',
    fallbackLabel: '下書き',
  },
  SUBMITTED: {
    color: '#075985',
    backgroundColor: '#E0F2FE',
    fallbackLabel: '申請中',
  },
  APPROVED: {
    color: '#166534',
    backgroundColor: '#DCFCE7',
    fallbackLabel: '承認済み',
  },
  RETURNED: {
    color: '#9A3412',
    backgroundColor: '#FFEDD5',
    fallbackLabel: '差戻し',
  },
}

interface StatusChipProps {
  status: ExpenseStatus
  label?: string
}

export function StatusChip({ status, label }: StatusChipProps) {
  const style = statusStyles[status]
  return (
    <Chip
      size="small"
      label={label ?? style.fallbackLabel}
      sx={{
        color: style.color,
        bgcolor: style.backgroundColor,
        fontWeight: 700,
      }}
    />
  )
}
