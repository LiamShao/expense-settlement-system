import type {
  ExpenseApplicationDetail,
  ExpenseStatus,
  Role,
  User,
} from '../api/types'
import {
  canAccessAuditLogs,
  canAccessReviews,
  getExpenseActions,
} from './permissions'

const roles: Role[] = ['USER', 'APPROVER', 'ADMIN']
const statuses: ExpenseStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'RETURNED',
]

function createUser(id: number, role: Role): User {
  return {
    id,
    employeeCode: `E00${id}`,
    name: `ユーザー${id}`,
    email: `user${id}@example.com`,
    role,
    roleName: role,
    department: '開発部',
  }
}

function createExpense(
  owner: User,
  status: ExpenseStatus,
): Pick<ExpenseApplicationDetail, 'applicant' | 'status'> {
  return { applicant: owner, status }
}

describe.each(roles)('%s の申請操作権限', (role) => {
  const user = createUser(1, role)

  it.each(statuses)('本人の %s に対する操作を判定する', (status) => {
    const actions = getExpenseActions(user, createExpense(user, status))
    const editable = status === 'DRAFT' || status === 'RETURNED'

    expect(actions).toEqual({
      edit: editable,
      delete: editable,
      submit: editable,
      approve: false,
      return: false,
    })
  })

  it.each(statuses)('他人の %s に対する操作を判定する', (status) => {
    const actions = getExpenseActions(
      user,
      createExpense(createUser(2, 'USER'), status),
    )
    const reviewable =
      status === 'SUBMITTED' && (role === 'APPROVER' || role === 'ADMIN')

    expect(actions).toEqual({
      edit: false,
      delete: false,
      submit: false,
      approve: reviewable,
      return: reviewable,
    })
  })
})

describe('role route permissions', () => {
  it.each([
    ['USER', false, false],
    ['APPROVER', true, false],
    ['ADMIN', true, true],
  ] as const)('%s のメニュー権限を判定する', (role, reviews, auditLogs) => {
    expect(canAccessReviews(role)).toBe(reviews)
    expect(canAccessAuditLogs(role)).toBe(auditLogs)
  })
})
