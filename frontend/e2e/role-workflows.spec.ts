import { expect, test, type Page } from '@playwright/test'

const password = 'Password123!'

async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByRole('textbox', { name: 'メールアドレス' }).fill(email)
  await page
    .getByRole('textbox', { name: 'パスワード', exact: true })
    .fill(password)
  await page.getByRole('button', { name: 'ログインする' }).click()
  await expect(page.getByRole('heading', { name: '申請一覧' })).toBeVisible()
}

async function logout(page: Page) {
  await page.getByRole('button', { name: 'ログアウト' }).click()
  const dialog = page.getByRole('dialog', { name: 'ログアウトしますか？' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'ログアウトする' }).click()
  await expect(page.getByRole('button', { name: 'ログインする' })).toBeVisible()
}

async function createExpense(page: Page, title: string) {
  await page
    .getByRole('navigation', { name: 'メインナビゲーション' })
    .getByRole('link', { name: '新規申請' })
    .click()
  await expect(
    page.getByRole('heading', { name: '経費申請を作成' }),
  ).toBeVisible()
  await page.getByRole('textbox', { name: '件名' }).fill(title)
  await page.getByLabel('利用日').fill('2026-07-18')
  await page.getByRole('spinbutton', { name: '金額' }).fill('13820')
  await page.getByRole('textbox', { name: '内容' }).fill('E2E 新幹線交通費')
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(
    page.getByRole('heading', { name: '経費申請詳細' }),
  ).toBeVisible()
}

async function submitExpense(page: Page) {
  await page.getByRole('button', { name: '申請', exact: true }).click()
  await page.getByRole('button', { name: '申請する' }).click()
  await expect(page.getByText('申請中', { exact: true })).toBeVisible()
}

async function openReview(page: Page, title: string) {
  await page
    .getByRole('navigation', { name: 'メインナビゲーション' })
    .getByRole('link', { name: '承認待ち' })
    .click()
  const row = page.getByRole('row').filter({ hasText: title })
  await expect(row).toBeVisible()
  await row.getByRole('link').click()
  await expect(page.getByText(title)).toBeVisible()
}

test('USER作成・編集・申請、APPROVER承認・差戻し、ADMIN監査検索', async ({
  page,
}) => {
  const runId = Date.now()
  const approvalTitle = `E2E承認-${runId}`
  const editedApprovalTitle = `${approvalTitle}-編集済み`
  const returnTitle = `E2E差戻し-${runId}`
  const returnReason = `E2E差戻し理由-${runId}`

  await test.step('USERが申請を作成・編集・申請する', async () => {
    await login(page, 'user@example.com')
    await createExpense(page, approvalTitle)

    await page.getByRole('link', { name: '編集' }).click()
    const titleInput = page.getByRole('textbox', { name: '件名' })
    await titleInput.fill(editedApprovalTitle)
    await page.getByRole('button', { name: '保存する' }).click()
    await expect(page.getByText(editedApprovalTitle)).toBeVisible()
    await submitExpense(page)

    await createExpense(page, returnTitle)
    await submitExpense(page)
    await logout(page)
  })

  await test.step('APPROVERが1件を承認し1件を差戻す', async () => {
    await login(page, 'approver@example.com')
    await openReview(page, editedApprovalTitle)
    await page.getByRole('button', { name: '承認', exact: true }).click()
    await page.getByRole('button', { name: '承認する' }).click()
    await expect(page.getByText('承認済み', { exact: true })).toBeVisible()

    await openReview(page, returnTitle)
    await page.getByRole('button', { name: '差戻し' }).click()
    await page.getByRole('textbox', { name: '差戻し理由' }).fill(returnReason)
    await page.getByRole('button', { name: '差戻す' }).click()
    await expect(page.getByText('差戻し', { exact: true })).toBeVisible()
    await logout(page)
  })

  await test.step('USERが差戻し理由を確認する', async () => {
    await login(page, 'user@example.com')
    await page.getByRole('textbox', { name: 'キーワード' }).fill(returnTitle)
    await page.getByRole('button', { name: '検索', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: returnTitle })
    await row.getByRole('link').click()
    await expect(page.getByText(returnReason)).toBeVisible()
    await logout(page)
  })

  await test.step('ADMINが承認・差戻しの監査ログを検索する', async () => {
    await login(page, 'admin@example.com')
    await page
      .getByRole('navigation', { name: 'メインナビゲーション' })
      .getByRole('link', { name: '監査ログ' })
      .click()
    await expect(page.getByRole('heading', { name: '監査ログ' })).toBeVisible()

    await page.getByLabel('Action').click()
    await page
      .getByRole('option', { name: 'EXPENSE_APPLICATION_APPROVE' })
      .click()
    await page.getByRole('button', { name: '検索', exact: true }).click()
    await expect(
      page.getByText('EXPENSE_APPLICATION_APPROVE', { exact: true }).first(),
    ).toBeVisible()

    await page.getByLabel('Action').click()
    await page
      .getByRole('option', { name: 'EXPENSE_APPLICATION_RETURN' })
      .click()
    await page.getByRole('button', { name: '検索', exact: true }).click()
    await expect(
      page.getByText('EXPENSE_APPLICATION_RETURN', { exact: true }).first(),
    ).toBeVisible()
  })
})
