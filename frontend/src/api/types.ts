export type Role = 'USER' | 'APPROVER' | 'ADMIN'

export type ExpenseStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'RETURNED'

export type ExpenseCategory =
  | 'TRANSPORTATION'
  | 'MEAL'
  | 'SUPPLIES'
  | 'ACCOMMODATION'
  | 'OTHER'

export interface User {
  id: number
  employeeCode: string
  name: string
  email: string
  role: Role
  roleName: string
  department: string
}

export interface AuthResponse {
  authenticationType: string
  user: User
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ExpenseApplicationSummary {
  id: number
  applicantId: number
  applicantName: string
  title: string
  status: ExpenseStatus
  statusName: string
  totalAmount: number
  submittedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ExpenseItem {
  id: number
  expenseDate: string
  category: ExpenseCategory
  categoryName: string
  amount: number
  description: string
  receiptObjectKey: string | null
}

export interface ExpenseApplicationDetail {
  id: number
  applicant: User
  title: string
  status: ExpenseStatus
  statusName: string
  totalAmount: number
  submittedAt: string | null
  approvedAt: string | null
  approver: User | null
  returnedAt: string | null
  returnReason: string | null
  items: ExpenseItem[]
  createdAt: string
  updatedAt: string
}

export interface ExpenseItemInput {
  expenseDate: string
  category: ExpenseCategory
  amount: number
  description: string
  receiptObjectKey?: string
}

export interface ExpenseApplicationInput {
  title: string
  items: ExpenseItemInput[]
}

export interface AuditLog {
  id: number
  userId: number
  userName: string
  action: string
  targetType: string
  targetId: number
  detail: string | null
  createdAt: string
}

export interface ValidationErrorDetail {
  field: string
  message: string
}
