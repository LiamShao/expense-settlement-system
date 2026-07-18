import type {
  ExpenseApplicationDetail,
  ExpenseStatus,
  Role,
  User,
} from '../api/types'

export interface ExpenseActions {
  edit: boolean
  delete: boolean
  submit: boolean
  approve: boolean
  return: boolean
}

export function canAccessReviews(role: Role): boolean {
  return role === 'APPROVER' || role === 'ADMIN'
}

export function canAccessAuditLogs(role: Role): boolean {
  return role === 'ADMIN'
}

export function getExpenseActions(
  user: User,
  expense: Pick<ExpenseApplicationDetail, 'applicant' | 'status'>,
): ExpenseActions {
  const isOwner = user.id === expense.applicant.id
  const isEditableStatus: ExpenseStatus[] = ['DRAFT', 'RETURNED']
  const canEdit = isOwner && isEditableStatus.includes(expense.status)
  const canReview =
    !isOwner &&
    expense.status === 'SUBMITTED' &&
    canAccessReviews(user.role)

  return {
    edit: canEdit,
    delete: canEdit,
    submit: canEdit,
    approve: canReview,
    return: canReview,
  }
}
