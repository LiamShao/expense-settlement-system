import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Link as MuiLink,
  Paper,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { ExpenseApplicationDetail } from '../../api/types'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingSkeleton } from '../../components/LoadingSkeleton'
import { PageHeader } from '../../components/PageHeader'
import { StatusChip } from '../../components/StatusChip'
import { formatDateTime, formatYen } from '../../utils/format'
import { getExpenseActions } from '../../utils/permissions'
import { useRouteSuccessMessage } from '../../utils/routeFeedback'
import { useAuth } from '../auth/AuthContext'

type DetailAction = 'delete' | 'submit' | 'approve'

const dialogContent: Record<
  DetailAction,
  {
    title: string
    confirmLabel: string
    color: 'primary' | 'error'
    endpoint: string
  }
> = {
  delete: {
    title: '経費申請を削除しますか？',
    confirmLabel: '削除する',
    color: 'error',
    endpoint: '',
  },
  submit: {
    title: '経費申請を申請しますか？',
    confirmLabel: '申請する',
    color: 'primary',
    endpoint: '/submit',
  },
  approve: {
    title: '経費申請を承認しますか？',
    confirmLabel: '承認する',
    color: 'primary',
    endpoint: '/approve',
  },
}

function DetailValue({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography sx={{ mt: 0.25 }}>{children || '-'}</Typography>
    </Box>
  )
}

interface ExpenseDetailPageProps {
  mode: 'expense' | 'review'
}

export function ExpenseDetailPage({ mode }: ExpenseDetailPageProps) {
  const { id = '' } = useParams()
  const numericId = Number(id)
  const { user, request } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [confirmAction, setConfirmAction] = useState<DetailAction | null>(null)
  const [returnOpen, setReturnOpen] = useState(false)
  const [returnReason, setReturnReason] = useState('')
  const [actionError, setActionError] = useState<unknown>(null)
  const [successMessage, setSuccessMessage] = useRouteSuccessMessage()
  const isReview = mode === 'review'
  const endpoint = isReview
    ? `/api/reviews/${numericId}`
    : `/api/expense-applications/${numericId}`

  const detailQuery = useQuery({
    queryKey: ['expense-detail', mode, numericId],
    queryFn: ({ signal }) =>
      request<ExpenseApplicationDetail>(endpoint, { signal }),
    enabled: Number.isInteger(numericId) && numericId > 0,
    retry: (failureCount, error) => {
      const status =
        typeof error === 'object' && error && 'status' in error
          ? Number(error.status)
          : 0
      return ![403, 404].includes(status) && failureCount < 1
    },
  })

  const actionMutation = useMutation({
    mutationFn: async ({
      action,
      reason,
    }: {
      action: DetailAction | 'return'
      reason?: string
    }) => {
      if (action === 'delete') {
        await request<null>(`/api/expense-applications/${numericId}`, {
          method: 'DELETE',
        })
        return null
      }

      const suffix =
        action === 'return' ? '/return' : dialogContent[action].endpoint
      return request<ExpenseApplicationDetail>(
        `/api/expense-applications/${numericId}${suffix}`,
        {
          method: 'POST',
          ...(action === 'return'
            ? { body: JSON.stringify({ returnReason: reason }) }
            : {}),
        },
      )
    },
    onSuccess: async (data, variables) => {
      setActionError(null)
      setConfirmAction(null)
      setReturnOpen(false)
      setReturnReason('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['expenses'] }),
        queryClient.invalidateQueries({ queryKey: ['reviews'] }),
      ])

      if (variables.action === 'delete') {
        navigate('/expenses', {
          replace: true,
          state: { successMessage: '経費申請を削除しました。' },
        })
        return
      }

      if (data) {
        queryClient.setQueryData(['expense-detail', mode, numericId], data)
      }
      const messages: Record<Exclude<typeof variables.action, 'delete'>, string> =
        {
          submit: '経費申請を申請しました。',
          approve: '経費申請を承認しました。',
          return: '経費申請を差戻しました。',
        }
      setSuccessMessage(messages[variables.action])
    },
    onError: (error) => {
      setActionError(error)
      setConfirmAction(null)
    },
  })

  if (!Number.isInteger(numericId) || numericId <= 0) {
    return (
      <ErrorAlert error={new Error('正しい申請IDを指定してください。')} />
    )
  }

  const expense = detailQuery.data
  const actions =
    user && expense
      ? getExpenseActions(user, expense)
      : { edit: false, delete: false, submit: false, approve: false, return: false }

  const runConfirmedAction = () => {
    if (confirmAction) {
      actionMutation.mutate({ action: confirmAction })
    }
  }

  const submitReturn = () => {
    const trimmedReason = returnReason.trim()
    if (!trimmedReason || trimmedReason.length > 1000) {
      return
    }
    actionMutation.mutate({ action: 'return', reason: trimmedReason })
  }

  return (
    <Stack spacing={3}>
      <PageHeader
        title="経費申請詳細"
        breadcrumbs={[
          { label: isReview ? '承認待ち' : '申請一覧', to: isReview ? '/reviews' : '/expenses' },
          { label: `#${numericId}` },
        ]}
        action={
          expense ? (
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ flexWrap: 'wrap' }}
            >
              {actions.edit && (
                <Button
                  component={Link}
                  to={`/expenses/${numericId}/edit`}
                  variant="outlined"
                >
                  編集
                </Button>
              )}
              {actions.delete && (
                <Button
                  color="error"
                  variant="outlined"
                  onClick={() => setConfirmAction('delete')}
                >
                  削除
                </Button>
              )}
              {actions.submit && (
                <Button
                  variant="contained"
                  onClick={() => setConfirmAction('submit')}
                >
                  申請
                </Button>
              )}
              {actions.return && (
                <Button
                  color="warning"
                  variant="outlined"
                  onClick={() => {
                    setActionError(null)
                    setReturnOpen(true)
                  }}
                >
                  差戻し
                </Button>
              )}
              {actions.approve && (
                <Button
                  variant="contained"
                  onClick={() => setConfirmAction('approve')}
                >
                  承認
                </Button>
              )}
            </Stack>
          ) : undefined
        }
      />

      {detailQuery.isPending && <LoadingSkeleton />}
      {detailQuery.isError && (
        <ErrorAlert
          error={detailQuery.error}
          onRetry={() => detailQuery.refetch()}
        />
      )}
      {Boolean(actionError) && <ErrorAlert error={actionError} />}

      {expense && (
        <>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
              sx={{ justifyContent: 'space-between' }}
            >
              <Box>
                <StatusChip
                  status={expense.status}
                  label={expense.statusName}
                />
                <Typography component="h2" variant="h2" sx={{ mt: 1.5 }}>
                  {expense.title}
                </Typography>
              </Box>
              <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
                <Typography variant="caption" color="text.secondary">
                  合計金額
                </Typography>
                <Typography variant="h1">
                  {formatYen(expense.totalAmount)}
                </Typography>
              </Box>
            </Stack>
            <Divider sx={{ my: 2.5 }} />
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: {
                  xs: '1fr',
                  sm: 'repeat(2, minmax(0, 1fr))',
                  lg: 'repeat(4, minmax(0, 1fr))',
                },
                gap: 2,
              }}
            >
              <DetailValue label="申請者">
                {expense.applicant.name}（{expense.applicant.employeeCode}）
              </DetailValue>
              <DetailValue label="部署">{expense.applicant.department}</DetailValue>
              <DetailValue label="作成日時">
                {formatDateTime(expense.createdAt)}
              </DetailValue>
              <DetailValue label="更新日時">
                {formatDateTime(expense.updatedAt)}
              </DetailValue>
              <DetailValue label="申請日時">
                {formatDateTime(expense.submittedAt)}
              </DetailValue>
              <DetailValue label="承認日時">
                {formatDateTime(expense.approvedAt)}
              </DetailValue>
              <DetailValue label="承認者">
                {expense.approver?.name ?? '-'}
              </DetailValue>
              <DetailValue label="差戻し日時">
                {formatDateTime(expense.returnedAt)}
              </DetailValue>
            </Box>
            {expense.returnReason && (
              <Alert severity="warning" sx={{ mt: 2.5 }}>
                <Typography sx={{ fontWeight: 700 }}>差戻し理由</Typography>
                {expense.returnReason}
              </Alert>
            )}
          </Paper>

          <Paper variant="outlined">
            <Typography component="h2" variant="h2" sx={{ p: 2.5 }}>
              明細
            </Typography>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table aria-label="経費明細">
                <TableHead>
                  <TableRow>
                    <TableCell>利用日</TableCell>
                    <TableCell>カテゴリ</TableCell>
                    <TableCell>内容</TableCell>
                    <TableCell>領収書 object key</TableCell>
                    <TableCell align="right">金額</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {expense.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {item.expenseDate}
                      </TableCell>
                      <TableCell>{item.categoryName}</TableCell>
                      <TableCell>{item.description}</TableCell>
                      <TableCell>
                        {item.receiptObjectKey ? (
                          <MuiLink
                            component="span"
                            underline="none"
                            color="text.secondary"
                            sx={{ overflowWrap: 'anywhere' }}
                          >
                            {item.receiptObjectKey}
                          </MuiLink>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell align="right">
                        {formatYen(item.amount)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}

      {confirmAction && expense && (
        <ConfirmDialog
          open
          title={dialogContent[confirmAction].title}
          description={
            confirmAction === 'delete'
              ? `申請 #${expense.id}「${expense.title}」を削除します。この操作は取り消せません。`
              : `申請 #${expense.id}「${expense.title}」を${dialogContent[
                  confirmAction
                ].confirmLabel.replace('する', '')}します。`
          }
          confirmLabel={dialogContent[confirmAction].confirmLabel}
          confirmColor={dialogContent[confirmAction].color}
          pending={actionMutation.isPending}
          onCancel={() => setConfirmAction(null)}
          onConfirm={runConfirmedAction}
        />
      )}

      <Dialog
        open={returnOpen}
        onClose={actionMutation.isPending ? undefined : () => setReturnOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>経費申請を差戻しますか？</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            申請者が修正できるよう、具体的な差戻し理由を入力してください。
          </Typography>
          <TextField
            autoFocus
            label="差戻し理由"
            multiline
            minRows={4}
            fullWidth
            required
            value={returnReason}
            onChange={(event) => setReturnReason(event.target.value)}
            error={returnReason.length > 1000}
            helperText={`${returnReason.length} / 1000文字`}
            slotProps={{ htmlInput: { maxLength: 1001 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setReturnOpen(false)}
            disabled={actionMutation.isPending}
          >
            キャンセル
          </Button>
          <Button
            variant="contained"
            color="warning"
            disabled={
              actionMutation.isPending ||
              !returnReason.trim() ||
              returnReason.length > 1000
            }
            onClick={submitReturn}
          >
            {actionMutation.isPending ? '処理中…' : '差戻す'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={Boolean(successMessage)}
        autoHideDuration={5000}
        onClose={() => setSuccessMessage(null)}
        message={successMessage}
      />
    </Stack>
  )
}
