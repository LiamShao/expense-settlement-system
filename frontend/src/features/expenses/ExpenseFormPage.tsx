import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import {
  Box,
  Button,
  FormControl,
  FormHelperText,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef } from 'react'
import {
  Controller,
  useFieldArray,
  useForm,
  useWatch,
  type FieldPath,
} from 'react-hook-form'
import { useBlocker, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../../api/client'
import type {
  ExpenseApplicationDetail,
  ExpenseApplicationInput,
  ExpenseCategory,
} from '../../api/types'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingSkeleton } from '../../components/LoadingSkeleton'
import { PageHeader } from '../../components/PageHeader'
import { formatYen } from '../../utils/format'
import { getExpenseActions } from '../../utils/permissions'
import { useAuth } from '../auth/AuthContext'

const maximumAmount = 999_999_999_999
const categoryCodes = [
  'TRANSPORTATION',
  'MEAL',
  'SUPPLIES',
  'ACCOMMODATION',
  'OTHER',
] as const

const categoryLabels: Record<ExpenseCategory, string> = {
  TRANSPORTATION: '交通費',
  MEAL: '会議費・飲食費',
  SUPPLIES: '消耗品費',
  ACCOMMODATION: '宿泊費',
  OTHER: 'その他',
}

const itemSchema = z.object({
  id: z.number().int().positive().optional(),
  expenseDate: z.string().min(1, '利用日を入力してください。'),
  category: z.enum(categoryCodes),
  amount: z
    .number({ error: '金額を入力してください。' })
    .int('金額は整数円で入力してください。')
    .min(1, '金額は1円以上で入力してください。')
    .max(maximumAmount, '金額が上限を超えています。'),
  description: z
    .string()
    .trim()
    .min(1, '内容を入力してください。')
    .max(500, '内容は500文字以内で入力してください。'),
  receiptObjectKey: z
    .string()
    .trim()
    .max(500, '領収書 object key は500文字以内で入力してください。'),
})

const expenseSchema = z
  .object({
    title: z
      .string()
      .trim()
      .min(1, '件名を入力してください。')
      .max(200, '件名は200文字以内で入力してください。'),
    items: z.array(itemSchema).min(1, '明細を1件以上入力してください。'),
  })
  .superRefine((value, context) => {
    const total = value.items.reduce((sum, item) => sum + item.amount, 0)
    if (total > maximumAmount) {
      context.addIssue({
        code: 'custom',
        path: ['items'],
        message: '合計金額が上限を超えています。',
      })
    }
  })

type ExpenseFormValues = z.infer<typeof expenseSchema>

const emptyItem = {
  expenseDate: '',
  category: 'TRANSPORTATION' as const,
  amount: undefined as unknown as number,
  description: '',
  receiptObjectKey: '',
}

interface ExpenseFormPageProps {
  mode: 'create' | 'edit'
}

export function ExpenseFormPage({ mode }: ExpenseFormPageProps) {
  const isEdit = mode === 'edit'
  const { id = '' } = useParams()
  const numericId = Number(id)
  const { user, request } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const initialized = useRef(false)

  const form = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      title: '',
      items: [emptyItem],
    },
  })
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = form
  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  })
  const watchedItems = useWatch({ control, name: 'items' })
  const total = useMemo(
    () =>
      (watchedItems ?? []).reduce(
        (sum, item) =>
          sum +
          (typeof item?.amount === 'number' && Number.isFinite(item.amount)
            ? item.amount
            : 0),
        0,
      ),
    [watchedItems],
  )

  const detailQuery = useQuery({
    queryKey: ['expense-detail', 'expense', numericId],
    queryFn: ({ signal }) =>
      request<ExpenseApplicationDetail>(
        `/api/expense-applications/${numericId}`,
        { signal },
      ),
    enabled: isEdit && Number.isInteger(numericId) && numericId > 0,
    retry: false,
  })

  useEffect(() => {
    if (!isEdit || !detailQuery.data || initialized.current) {
      return
    }
    initialized.current = true
    reset({
      title: detailQuery.data.title,
      items: detailQuery.data.items.map((item) => ({
        id: item.id,
        expenseDate: item.expenseDate,
        category: item.category,
        amount: item.amount,
        description: item.description,
        receiptObjectKey: item.receiptObjectKey ?? '',
      })),
    })
  }, [detailQuery.data, isEdit, reset])

  useEffect(() => {
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      if (isDirty) {
        event.preventDefault()
      }
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [isDirty])

  const blocker = useBlocker(isDirty && !isSubmitting)

  const saveMutation = useMutation({
    mutationFn: (input: ExpenseApplicationInput) =>
      request<ExpenseApplicationDetail>(
        isEdit
          ? `/api/expense-applications/${numericId}`
          : '/api/expense-applications',
        {
          method: isEdit ? 'PUT' : 'POST',
          body: JSON.stringify(input),
        },
      ),
  })

  const onSubmit = async (values: ExpenseFormValues) => {
    const input: ExpenseApplicationInput = {
      title: values.title.trim(),
      items: values.items.map((item) => ({
        ...(item.id ? { id: item.id } : {}),
        expenseDate: item.expenseDate,
        category: item.category,
        amount: item.amount,
        description: item.description.trim(),
        ...(item.receiptObjectKey.trim()
          ? { receiptObjectKey: item.receiptObjectKey.trim() }
          : {}),
      })),
    }

    try {
      const saved = await saveMutation.mutateAsync(input)
      reset(values)
      await queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.setQueryData(['expense-detail', 'expense', saved.id], saved)
      navigate(`/expenses/${saved.id}`, {
        replace: true,
        state: {
          successMessage: isEdit
            ? '経費申請を更新しました。'
            : '経費申請を作成しました。',
        },
      })
    } catch (error) {
      if (error instanceof ApiError) {
        error.details.forEach((detail) => {
          const normalizedPath = detail.field
            .replace(/^items\[(\d+)\]\./, 'items.$1.')
            .replace(/^items\.(\d+)\./, 'items.$1.') as FieldPath<ExpenseFormValues>
          setError(normalizedPath, { message: detail.message })
        })
      }
    }
  }

  if (isEdit && (!Number.isInteger(numericId) || numericId <= 0)) {
    return <ErrorAlert error={new Error('正しい申請IDを指定してください。')} />
  }

  const editable =
    !isEdit ||
    (user && detailQuery.data
      ? getExpenseActions(user, detailQuery.data).edit
      : false)

  return (
    <Stack spacing={3} sx={{ maxWidth: 960 }}>
      <PageHeader
        title={isEdit ? '経費申請を編集' : '経費申請を作成'}
        breadcrumbs={[
          { label: '申請一覧', to: '/expenses' },
          ...(isEdit
            ? [
                {
                  label: `#${numericId}`,
                  to: `/expenses/${numericId}`,
                },
                { label: '編集' },
              ]
            : [{ label: '新規申請' }]),
        ]}
      />

      {isEdit && detailQuery.isPending && <LoadingSkeleton />}
      {isEdit && detailQuery.isError && (
        <ErrorAlert
          error={detailQuery.error}
          onRetry={() => detailQuery.refetch()}
        />
      )}
      {isEdit && detailQuery.data && !editable && (
        <ErrorAlert
          error={
            new Error('この申請は現在のユーザーまたは状態では編集できません。')
          }
        />
      )}
      {saveMutation.isError && <ErrorAlert error={saveMutation.error} />}

      {editable && (
        <Stack
          component="form"
          spacing={3}
          noValidate
          onSubmit={handleSubmit(onSubmit)}
        >
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography component="h2" variant="h2" sx={{ mb: 2 }}>
              基本情報
            </Typography>
            <TextField
              label="件名"
              fullWidth
              required
              error={Boolean(errors.title)}
              helperText={errors.title?.message}
              slotProps={{ htmlInput: { maxLength: 200 } }}
              {...register('title')}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Stack
              direction="row"
              sx={{
                mb: 2,
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <Typography component="h2" variant="h2">
                明細
              </Typography>
              <Button
                type="button"
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={() => append(emptyItem)}
              >
                明細追加
              </Button>
            </Stack>

            <Stack spacing={2}>
              {fields.map((field, index) => {
                const itemErrors = errors.items?.[index]
                return (
                  <Paper
                    variant="outlined"
                    key={field.id}
                    sx={{ p: 2, bgcolor: 'background.default' }}
                  >
                    <Stack
                      direction="row"
                      sx={{
                        mb: 2,
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <Typography sx={{ fontWeight: 700 }}>
                        明細 #{index + 1}
                      </Typography>
                      <Tooltip
                        title={
                          fields.length === 1
                            ? '明細は1件以上必要です'
                            : 'この明細を削除'
                        }
                      >
                        <span>
                          <IconButton
                            type="button"
                            color="error"
                            aria-label={`明細 ${index + 1} を削除`}
                            disabled={fields.length === 1}
                            onClick={() => remove(index)}
                          >
                            <DeleteOutlineIcon />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </Stack>
                    <Box
                      sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                          xs: '1fr',
                          sm: 'repeat(2, minmax(0, 1fr))',
                          md: 'repeat(3, minmax(0, 1fr))',
                        },
                        gap: 2,
                      }}
                    >
                      <TextField
                        label="利用日"
                        type="date"
                        required
                        error={Boolean(itemErrors?.expenseDate)}
                        helperText={itemErrors?.expenseDate?.message}
                        slotProps={{ inputLabel: { shrink: true } }}
                        {...register(`items.${index}.expenseDate`)}
                      />
                      <Controller
                        name={`items.${index}.category`}
                        control={control}
                        render={({ field: categoryField }) => (
                          <FormControl error={Boolean(itemErrors?.category)}>
                            <InputLabel id={`category-${index}-label`}>
                              カテゴリ
                            </InputLabel>
                            <Select
                              {...categoryField}
                              labelId={`category-${index}-label`}
                              label="カテゴリ"
                            >
                              {categoryCodes.map((category) => (
                                <MenuItem value={category} key={category}>
                                  {categoryLabels[category]}
                                </MenuItem>
                              ))}
                            </Select>
                            {itemErrors?.category?.message && (
                              <FormHelperText>
                                {itemErrors.category.message}
                              </FormHelperText>
                            )}
                          </FormControl>
                        )}
                      />
                      <TextField
                        label="金額"
                        type="number"
                        required
                        error={Boolean(itemErrors?.amount)}
                        helperText={itemErrors?.amount?.message}
                        slotProps={{
                          htmlInput: {
                            min: 1,
                            max: maximumAmount,
                            step: 1,
                            inputMode: 'numeric',
                          },
                        }}
                        {...register(`items.${index}.amount`, {
                          valueAsNumber: true,
                        })}
                      />
                      <TextField
                        label="内容"
                        required
                        multiline
                        minRows={2}
                        error={Boolean(itemErrors?.description)}
                        helperText={itemErrors?.description?.message}
                        slotProps={{ htmlInput: { maxLength: 500 } }}
                        sx={{ gridColumn: { sm: '1 / -1', md: 'span 2' } }}
                        {...register(`items.${index}.description`)}
                      />
                      <TextField
                        label="領収書 object key"
                        error={Boolean(itemErrors?.receiptObjectKey)}
                        helperText={
                          itemErrors?.receiptObjectKey?.message ??
                          'アップロード機能は未実装です。'
                        }
                        slotProps={{ htmlInput: { maxLength: 500 } }}
                        {...register(`items.${index}.receiptObjectKey`)}
                      />
                    </Box>
                  </Paper>
                )
              })}
            </Stack>

            {typeof errors.items?.message === 'string' && (
              <FormHelperText error sx={{ mt: 2 }}>
                {errors.items.message}
              </FormHelperText>
            )}
            <Stack
              direction="row"
              spacing={2}
              sx={{
                mt: 3,
                justifyContent: 'flex-end',
                alignItems: 'baseline',
              }}
            >
              <Typography color="text.secondary">合計金額</Typography>
              <Typography variant="h1">{formatYen(total)}</Typography>
            </Stack>
          </Paper>

          <Stack
            direction={{ xs: 'column-reverse', sm: 'row' }}
            spacing={1}
            sx={{ justifyContent: 'space-between' }}
          >
            <Button
              type="button"
              onClick={() =>
                navigate(isEdit ? `/expenses/${numericId}` : '/expenses')
              }
            >
              キャンセル
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={isSubmitting || saveMutation.isPending}
            >
              {saveMutation.isPending ? '保存中…' : '保存する'}
            </Button>
          </Stack>
        </Stack>
      )}

      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="入力内容を破棄しますか？"
        description="保存していない変更があります。このページから移動すると入力内容は失われます。"
        confirmLabel="破棄して移動"
        confirmColor="error"
        onCancel={() => blocker.reset?.()}
        onConfirm={() => blocker.proceed?.()}
      />
    </Stack>
  )
}
