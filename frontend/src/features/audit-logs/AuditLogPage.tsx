import {
  Box,
  Button,
  FormControl,
  InputLabel,
  Link as MuiLink,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { AuditLog, PageResponse } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingSkeleton } from '../../components/LoadingSkeleton'
import { PageHeader } from '../../components/PageHeader'
import { formatDateTime } from '../../utils/format'
import { buildQueryString, readPositiveInteger } from '../../utils/query'
import { useAuth } from '../auth/AuthContext'

const actionOptions = [
  'EXPENSE_APPLICATION_CREATE',
  'EXPENSE_APPLICATION_UPDATE',
  'EXPENSE_APPLICATION_DELETE',
  'EXPENSE_APPLICATION_SUBMIT',
  'EXPENSE_APPLICATION_APPROVE',
  'EXPENSE_APPLICATION_RETURN',
]

interface AuditSearchValues {
  userId: string
  action: string
  targetType: string
  createdDateFrom: string
  createdDateTo: string
}

const defaultValues: AuditSearchValues = {
  userId: '',
  action: '',
  targetType: '',
  createdDateFrom: '',
  createdDateTo: '',
}

function readValues(searchParams: URLSearchParams): AuditSearchValues {
  return {
    userId: searchParams.get('userId') ?? '',
    action: searchParams.get('action') ?? '',
    targetType: searchParams.get('targetType') ?? '',
    createdDateFrom: searchParams.get('createdDateFrom') ?? '',
    createdDateTo: searchParams.get('createdDateTo') ?? '',
  }
}

export function AuditLogPage() {
  const { request } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [values, setValues] = useState(() => readValues(searchParams))
  const [dateError, setDateError] = useState<string | null>(null)
  const page = readPositiveInteger(searchParams.get('page'), 0)
  const size = [20, 50, 100].includes(Number(searchParams.get('size')))
    ? Number(searchParams.get('size'))
    : 20

  useEffect(() => {
    // URL is external navigation state; synchronize drafts for back/forward.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setValues(readValues(searchParams))
  }, [searchParams])

  const apiQuery = buildQueryString({
    userId: searchParams.get('userId'),
    action: searchParams.get('action'),
    targetType: searchParams.get('targetType'),
    createdDateFrom: searchParams.get('createdDateFrom'),
    createdDateTo: searchParams.get('createdDateTo'),
    page,
    size,
  })
  const query = useQuery({
    queryKey: ['audit-logs', apiQuery],
    queryFn: ({ signal }) =>
      request<PageResponse<AuditLog>>(`/api/audit-logs${apiQuery}`, {
        signal,
      }),
  })

  useEffect(() => {
    const totalPages = query.data?.totalPages
    if (totalPages && page >= totalPages) {
      const next = new URLSearchParams(searchParams)
      next.set('page', String(totalPages - 1))
      next.set('size', String(size))
      setSearchParams(next, { replace: true })
    }
  }, [page, query.data?.totalPages, searchParams, setSearchParams, size])

  const updateValue = (name: keyof AuditSearchValues, value: string) => {
    setValues((current) => ({ ...current, [name]: value }))
  }

  const applySearch = (event: FormEvent) => {
    event.preventDefault()
    if (
      values.createdDateFrom &&
      values.createdDateTo &&
      values.createdDateFrom > values.createdDateTo
    ) {
      setDateError('作成日Toは作成日From以降の日付を入力してください。')
      return
    }
    setDateError(null)
    setSearchParams({
      ...(values.userId ? { userId: values.userId } : {}),
      ...(values.action ? { action: values.action } : {}),
      ...(values.targetType ? { targetType: values.targetType } : {}),
      ...(values.createdDateFrom
        ? { createdDateFrom: values.createdDateFrom }
        : {}),
      ...(values.createdDateTo ? { createdDateTo: values.createdDateTo } : {}),
      page: '0',
      size: String(size),
    })
  }

  const resetSearch = () => {
    setValues(defaultValues)
    setDateError(null)
    setSearchParams({ page: '0', size: String(size) })
  }

  const updatePagination = (nextPage: number, nextSize = size) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    next.set('size', String(nextSize))
    setSearchParams(next)
  }

  return (
    <Stack spacing={3}>
      <PageHeader
        title="監査ログ"
        description="経費申請に対する業務操作を検索・確認します。"
      />

      <Paper
        component="form"
        variant="outlined"
        onSubmit={applySearch}
        sx={{ p: 3 }}
      >
        <Typography component="h2" variant="h2" sx={{ mb: 2 }}>
          検索条件
        </Typography>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: {
              xs: '1fr',
              md: 'repeat(2, minmax(0, 1fr))',
              lg: 'repeat(3, minmax(0, 1fr))',
            },
            gap: 2,
          }}
        >
          <TextField
            label="User ID"
            type="number"
            value={values.userId}
            onChange={(event) => updateValue('userId', event.target.value)}
            slotProps={{ htmlInput: { min: 1, step: 1 } }}
          />
          <FormControl>
            <InputLabel id="audit-action-label">Action</InputLabel>
            <Select
              labelId="audit-action-label"
              label="Action"
              value={values.action}
              onChange={(event) => updateValue('action', event.target.value)}
            >
              <MenuItem value="">すべて</MenuItem>
              {actionOptions.map((action) => (
                <MenuItem value={action} key={action}>
                  {action}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl>
            <InputLabel id="target-type-label">Target</InputLabel>
            <Select
              labelId="target-type-label"
              label="Target"
              value={values.targetType}
              onChange={(event) =>
                updateValue('targetType', event.target.value)
              }
            >
              <MenuItem value="">すべて</MenuItem>
              <MenuItem value="EXPENSE_APPLICATION">
                EXPENSE_APPLICATION
              </MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="作成日From"
            type="date"
            value={values.createdDateFrom}
            onChange={(event) =>
              updateValue('createdDateFrom', event.target.value)
            }
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="作成日To"
            type="date"
            value={values.createdDateTo}
            onChange={(event) =>
              updateValue('createdDateTo', event.target.value)
            }
            error={Boolean(dateError)}
            helperText={dateError}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Box>
        <Stack
          direction={{ xs: 'column-reverse', sm: 'row' }}
          spacing={1}
          sx={{ mt: 2, justifyContent: 'flex-end' }}
        >
          <Button type="button" onClick={resetSearch}>
            条件をクリア
          </Button>
          <Button type="submit" variant="contained">
            検索
          </Button>
        </Stack>
      </Paper>

      {query.isPending && <LoadingSkeleton />}
      {query.isError && (
        <ErrorAlert error={query.error} onRetry={() => query.refetch()} />
      )}
      {query.data && (
        <Paper variant="outlined">
          <Typography component="h2" variant="h2" sx={{ p: 2.5, pb: 1 }}>
            検索結果 {query.data.totalElements}件
          </Typography>
          {query.data.content.length === 0 ? (
            <EmptyState
              title="監査ログはありません"
              description="検索条件を変更して再度お試しください。"
              actionLabel="条件をクリア"
              onAction={resetSearch}
            />
          ) : (
            <>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table stickyHeader aria-label="監査ログ一覧">
                  <TableHead>
                    <TableRow>
                      <TableCell>日時</TableCell>
                      <TableCell>User</TableCell>
                      <TableCell>Action</TableCell>
                      <TableCell>Target</TableCell>
                      <TableCell>Detail</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {query.data.content.map((log) => (
                      <TableRow hover key={log.id}>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {formatDateTime(log.createdAt)}
                        </TableCell>
                        <TableCell>
                          {log.userName}（#{log.userId}）
                        </TableCell>
                        <TableCell>{log.action}</TableCell>
                        <TableCell>
                          {log.targetType === 'EXPENSE_APPLICATION' ? (
                            <MuiLink
                              component={Link}
                              to={`/expenses/${log.targetId}`}
                            >
                              {log.targetType} #{log.targetId}
                            </MuiLink>
                          ) : (
                            `${log.targetType} #${log.targetId}`
                          )}
                        </TableCell>
                        <TableCell>{log.detail ?? '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={query.data.totalElements}
                page={Math.min(page, Math.max(query.data.totalPages - 1, 0))}
                rowsPerPage={size}
                rowsPerPageOptions={[20, 50, 100]}
                labelRowsPerPage="表示件数"
                labelDisplayedRows={({ from, to, count }) =>
                  `${from}–${to} / ${count}`
                }
                onPageChange={(_, nextPage) => updatePagination(nextPage)}
                onRowsPerPageChange={(event) =>
                  updatePagination(0, Number(event.target.value))
                }
              />
            </>
          )}
        </Paper>
      )}
    </Stack>
  )
}
