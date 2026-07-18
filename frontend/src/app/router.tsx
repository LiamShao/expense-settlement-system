/* eslint-disable react-refresh/only-export-components */
import {
  Navigate,
  createBrowserRouter,
  type RouteObject,
} from 'react-router-dom'
import { lazy, Suspense, type ReactNode } from 'react'
import { ForbiddenPage, NotFoundPage } from './ErrorPages'
import { ProtectedRoute, RoleRoute } from './RouteGuards'
import { LoginPage } from '../features/auth/LoginPage'
import { LoadingSkeleton } from '../components/LoadingSkeleton'

const AppLayout = lazy(() =>
  import('./AppLayout').then((module) => ({
    default: module.AppLayout,
  })),
)
const AuditLogPage = lazy(() =>
  import('../features/audit-logs/AuditLogPage').then((module) => ({
    default: module.AuditLogPage,
  })),
)
const ExpenseDetailPage = lazy(() =>
  import('../features/expenses/ExpenseDetailPage').then((module) => ({
    default: module.ExpenseDetailPage,
  })),
)
const ExpenseFormPage = lazy(() =>
  import('../features/expenses/ExpenseFormPage').then((module) => ({
    default: module.ExpenseFormPage,
  })),
)
const ExpenseListPage = lazy(() =>
  import('../features/expenses/ExpenseListPage').then((module) => ({
    default: module.ExpenseListPage,
  })),
)
const ReviewListPage = lazy(() =>
  import('../features/reviews/ReviewListPage').then((module) => ({
    default: module.ReviewListPage,
  })),
)

function loadPage(page: ReactNode) {
  return <Suspense fallback={<LoadingSkeleton />}>{page}</Suspense>
}

export const appRoutes: RouteObject[] = [
    {
      path: '/login',
      element: <LoginPage />,
    },
    {
      element: (
        <ProtectedRoute>
          {loadPage(<AppLayout />)}
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: <Navigate to="/expenses" replace />,
        },
        {
          path: '/expenses',
          element: loadPage(<ExpenseListPage />),
        },
        {
          path: '/expenses/new',
          element: loadPage(<ExpenseFormPage mode="create" />),
        },
        {
          path: '/expenses/:id',
          element: loadPage(<ExpenseDetailPage mode="expense" />),
        },
        {
          path: '/expenses/:id/edit',
          element: loadPage(<ExpenseFormPage mode="edit" />),
        },
        {
          path: '/reviews',
          element: (
            <RoleRoute roles={['APPROVER', 'ADMIN']}>
              {loadPage(<ReviewListPage />)}
            </RoleRoute>
          ),
        },
        {
          path: '/reviews/:id',
          element: (
            <RoleRoute roles={['APPROVER', 'ADMIN']}>
              {loadPage(<ExpenseDetailPage mode="review" />)}
            </RoleRoute>
          ),
        },
        {
          path: '/audit-logs',
          element: (
            <RoleRoute roles={['ADMIN']}>
              {loadPage(<AuditLogPage />)}
            </RoleRoute>
          ),
        },
        {
          path: '/forbidden',
          element: <ForbiddenPage />,
        },
        {
          path: '*',
          element: <NotFoundPage />,
        },
      ],
    },
]

export function createAppRouter() {
  return createBrowserRouter(appRoutes)
}
