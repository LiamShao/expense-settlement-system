import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { createMemoryRouter } from 'react-router-dom'
import App from './App'
import { appRoutes } from './app/router'
import { server } from './test/server'

describe('App', () => {
  const currentUser = {
    id: 1,
    employeeCode: 'E001',
    name: '一般ユーザー',
    email: 'user@example.com',
    role: 'USER',
    roleName: '一般ユーザー',
    department: '開発部',
  }

  function mockLogin() {
    server.use(
      http.post('*/api/auth/login', async ({ request }) => {
        const body = (await request.json()) as {
          email: string
          password: string
        }
        if (
          body.email !== 'user@example.com' ||
          body.password !== 'Password123!'
        ) {
          return HttpResponse.json(
            {
              success: false,
              code: 'UNAUTHORIZED',
              message: 'メールアドレスまたはパスワードが正しくありません。',
            },
            { status: 401 },
          )
        }
        return HttpResponse.json({
          success: true,
          data: { authenticationType: 'Basic', user: currentUser },
        })
      }),
      http.get('*/api/auth/me', ({ request }) => {
        expect(request.headers.get('Authorization')).toMatch(/^Basic /)
        return HttpResponse.json({ success: true, data: currentUser })
      }),
    )
  }

  it('未認証ではログイン画面を表示する', () => {
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/login'],
    })
    render(<App router={router} />)
    expect(
      screen.getByRole('heading', { name: '経費精算システム' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ログインする' })).toBeEnabled()
  })

  it('ログイン後にBasic認証で申請一覧を取得する', async () => {
    const user = userEvent.setup()
    mockLogin()
    server.use(
      http.get('*/api/expense-applications', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [
              {
                id: 10,
                applicantId: 1,
                applicantName: '一般ユーザー',
                title: '東京出張交通費',
                status: 'DRAFT',
                statusName: '下書き',
                totalAmount: 1200,
                submittedAt: null,
                createdAt: '2026-07-18T09:00:00',
                updatedAt: '2026-07-18T10:00:00',
              },
            ],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          },
        }),
      ),
    )
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/login'],
    })
    render(<App router={router} />)

    await user.type(
      screen.getByRole('textbox', { name: 'メールアドレス' }),
      'user@example.com',
    )
    await user.type(screen.getByLabelText('パスワード'), 'Password123!')
    await user.click(screen.getByRole('button', { name: 'ログインする' }))

    expect(
      await screen.findByRole('heading', { name: '申請一覧' }),
    ).toBeVisible()
    expect(await screen.findByText('東京出張交通費')).toBeVisible()
    expect(window.localStorage).toHaveLength(0)
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('ログアウトは確認後に実行し、キャンセル時はセッションを維持する', async () => {
    const user = userEvent.setup()
    mockLogin()
    server.use(
      http.get('*/api/expense-applications', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
          },
        }),
      ),
    )
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/login'],
    })
    render(<App router={router} />)

    await user.type(
      screen.getByRole('textbox', { name: 'メールアドレス' }),
      'user@example.com',
    )
    await user.type(screen.getByLabelText('パスワード'), 'Password123!')
    await user.click(screen.getByRole('button', { name: 'ログインする' }))
    await screen.findByRole('heading', { name: '申請一覧' })

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    expect(
      screen.getByRole('dialog', { name: 'ログアウトしますか？' }),
    ).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    await waitForElementToBeRemoved(() =>
      screen.queryByRole('dialog', { name: 'ログアウトしますか？' }),
    )
    expect(
      screen.getByRole('heading', { name: '申請一覧' }),
    ).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    await user.click(screen.getByRole('button', { name: 'ログアウトする' }))

    expect(
      await screen.findByRole('button', { name: 'ログインする' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('未保存の新規申請で必須項目を検証する', async () => {
    const user = userEvent.setup()
    mockLogin()
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/expenses/new'],
    })
    render(<App router={router} />)

    await user.type(
      await screen.findByRole('textbox', { name: 'メールアドレス' }),
      'user@example.com',
    )
    await user.type(screen.getByLabelText('パスワード'), 'Password123!')
    await user.click(screen.getByRole('button', { name: 'ログインする' }))
    await screen.findByRole('heading', { name: '経費申請を作成' })

    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('件名を入力してください。')).toBeVisible()
    expect(screen.getByText('利用日を入力してください。')).toBeVisible()
    expect(screen.getByText('金額を入力してください。')).toBeVisible()
    expect(screen.getByText('内容を入力してください。')).toBeVisible()
  })
})
