import AddIcon from '@mui/icons-material/Add'
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  Link as MuiLink,
  MenuItem,
  Paper,
  Select,
  Snackbar,
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
import type {
  ExpenseApplicationSummary,
  ExpenseStatus,
  PageResponse,
} from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingSkeleton } from '../../components/LoadingSkeleton'
import { PageHeader } from '../../components/PageHeader'
import { StatusChip } from '../../components/StatusChip'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime, formatYen } from '../../utils/format'
import { buildQueryString, readPositiveInteger } from '../../utils/query'
import { useRouteSuccessMessage } from '../../utils/routeFeedback'

interface SearchValues {
  status: ExpenseStatus | ''
  keyword: string
  applicantId: string
  expenseDateFrom: string
  expenseDateTo: string
}

const defaultValues: SearchValues = {
  status: '',
  keyword: '',
  applicantId: '',
  expenseDateFrom: '',
  expenseDateTo: '',
}

const statusOptions: { value: ExpenseStatus; label: string }[] = [
  { value: 'DRAFT', label: '下書き' },
  { value: 'SUBMITTED', label: '申請中' },
  { value: 'APPROVED', label: '承認済み' },
  { value: 'RETURNED', label: '差戻し' },
]

function readValues(searchParams: URLSearchParams): SearchValues {
  const status = searchParams.get('status')
  return {
    status: statusOptions.some((option) => option.value === status)
      ? (status as ExpenseStatus)
      : '',
    keyword: searchParams.get('keyword') ?? '',
    applicantId: searchParams.get('applicantId') ?? '',
    expenseDateFrom: searchParams.get('expenseDateFrom') ?? '',
    expenseDateTo: searchParams.get('expenseDateTo') ?? '',
  }
}

interface ExpenseSearchPageProps {
  mode: 'expenses' | 'reviews'
}

export function ExpenseSearchPage({ mode }: ExpenseSearchPageProps) {
  const { user, request } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [values, setValues] = useState(() => readValues(searchParams))
  const [dateError, setDateError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useRouteSuccessMessage()
  const page = readPositiveInteger(searchParams.get('page'), 0)
  const size = [20, 50, 100].includes(Number(searchParams.get('size')))
    ? Number(searchParams.get('size'))
    : 20
  const isReviews = mode === 'reviews'
  const showApplicant = isReviews || user?.role === 'ADMIN'

  useEffect(() => {
    // URL is external navigation state; synchronize drafts for back/forward.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setValues(readValues(searchParams))
  }, [searchParams])

  const apiQuery = buildQueryString({
    applicantId: searchParams.get('applicantId'),
    status: isReviews ? undefined : searchParams.get('status'),
    keyword: searchParams.get('keyword'),
    expenseDateFrom: searchParams.get('expenseDateFrom'),
    expenseDateTo: searchParams.get('expenseDateTo'),
    page,
    size,
  })
  const endpoint = isReviews ? '/api/reviews' : '/api/expense-applications'
  const query = useQuery({
    queryKey: [mode, apiQuery],
    queryFn: ({ signal }) =>
      request<PageResponse<ExpenseApplicationSummary>>(
        `${endpoint}${apiQuery}`,
        { signal },
      ),
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

  const updateValue = (name: keyof SearchValues, value: string) => {
    setValues((current) => ({ ...current, [name]: value }))
  }

  const applySearch = (event: FormEvent) => {
    event.preventDefault()
    if (
      values.expenseDateFrom &&
      values.expenseDateTo &&
      values.expenseDateFrom > values.expenseDateTo
    ) {
      setDateError('利用日Toは利用日From以降の日付を入力してください。')
      return
    }

    setDateError(null)
    setSearchParams({
      ...(values.status && !isReviews ? { status: values.status } : {}),
      ...(values.keyword.trim() ? { keyword: values.keyword.trim() } : {}),
      ...(showApplicant && values.applicantId
        ? { applicantId: values.applicantId }
        : {}),
      ...(values.expenseDateFrom
        ? { expenseDateFrom: values.expenseDateFrom }
        : {}),
      ...(values.expenseDateTo ? { expenseDateTo: values.expenseDateTo } : {}),
      page: '0',
      size: String(size),
    })
  }

  const resetSearch = () => {
    setValues(defaultValues)
    setDateError(null)
    setSearchParams({ page: '0', size: String(size) })
  }

  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    next.set('size', String(size))
    setSearchParams(next)
  }

  const changeSize = (nextSize: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', '0')
    next.set('size', String(nextSize))
    setSearchParams(next)
  }

  return (
    <Stack spacing={3}>
      <PageHeader
        title={isReviews ? '承認待ち' : '申請一覧'}
        description={
          isReviews
            ? '他の申請者から提出された申請を確認します。'
            : '経費申請を検索・確認します。'
        }
        action={
          !isReviews ? (
            <Button
              component={Link}
              to="/expenses/new"
              variant="contained"
              startIcon={<AddIcon />}
            >
              新規申請
            </Button>
          ) : undefined
        }
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
          {!isReviews && (
            <FormControl>
              <InputLabel id="expense-status-label">ステータス</InputLabel>
              <Select
                labelId="expense-status-label"
                label="ステータス"
                value={values.status}
                onChange={(event) => updateValue('status', event.target.value)}
              >
                <MenuItem value="">すべて</MenuItem>
                {statusOptions.map((option) => (
                  <MenuItem value={option.value} key={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
          <TextField
            label="キーワード"
            value={values.keyword}
            onChange={(event) => updateValue('keyword', event.target.value)}
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />
          {showApplicant && (
            <TextField
              label="申請者ID"
              type="number"
              value={values.applicantId}
              onChange={(event) =>
                updateValue('applicantId', event.target.value)
              }
              slotProps={{ htmlInput: { min: 1, step: 1 } }}
            />
          )}
          <TextField
            label="利用日From"
            type="date"
            value={values.expenseDateFrom}
            onChange={(event) =>
              updateValue('expenseDateFrom', event.target.value)
            }
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="利用日To"
            type="date"
            value={values.expenseDateTo}
            onChange={(event) =>
              updateValue('expenseDateTo', event.target.value)
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
              title="該当する申請はありません"
              description={
                isReviews
                  ? '現在、条件に一致する承認待ち申請はありません。'
                  : '検索条件を変更するか、新しい申請を作成してください。'
              }
              actionLabel={isReviews ? '条件をクリア' : '新規申請'}
              actionTo={isReviews ? undefined : '/expenses/new'}
              onAction={isReviews ? resetSearch : undefined}
            />
          ) : (
            <>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table stickyHeader aria-label={isReviews ? '承認待ち一覧' : '申請一覧'}>
                  <TableHead>
                    <TableRow>
                      <TableCell>ID</TableCell>
                      <TableCell>申請者</TableCell>
                      <TableCell>件名</TableCell>
                      {!isReviews && <TableCell>ステータス</TableCell>}
                      <TableCell align="right">合計金額</TableCell>
                      <TableCell>
                        {isReviews ? '申請日時' : '更新日時'}
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {query.data.content.map((expense) => (
                      <TableRow hover key={expense.id}>
                        <TableCell>
                          <MuiLink
                            component={Link}
                            to={
                              isReviews
                                ? `/reviews/${expense.id}`
                                : `/expenses/${expense.id}`
                            }
                            aria-label={`申請 #${expense.id} の詳細`}
                          >
                            #{expense.id}
                          </MuiLink>
                        </TableCell>
                        <TableCell>{expense.applicantName}</TableCell>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Typography noWrap title={expense.title}>
                            {expense.title}
                          </Typography>
                        </TableCell>
                        {!isReviews && (
                          <TableCell>
                            <StatusChip
                              status={expense.status}
                              label={expense.statusName}
                            />
                          </TableCell>
                        )}
                        <TableCell align="right">
                          {formatYen(expense.totalAmount)}
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {formatDateTime(
                            isReviews ? expense.submittedAt : expense.updatedAt,
                          )}
                        </TableCell>
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
                onPageChange={(_, nextPage) => changePage(nextPage)}
                onRowsPerPageChange={(event) =>
                  changeSize(Number(event.target.value))
                }
              />
            </>
          )}
        </Paper>
      )}
      <Snackbar
        open={Boolean(successMessage)}
        autoHideDuration={5000}
        onClose={() => setSuccessMessage(null)}
        message={successMessage}
      />
    </Stack>
  )
}
