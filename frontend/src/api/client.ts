import type { CsrfTokenResponse, ValidationErrorDetail } from './types'

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string | null
}

interface ErrorResponse {
  success: false
  code: string
  message: string
  details?: ValidationErrorDetail[]
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code = 'UNKNOWN_ERROR',
    public readonly details: ValidationErrorDetail[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface ApiRequestOptions extends RequestInit {
  onUnauthorized?: () => void
}

const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS'])
let csrfToken: CsrfTokenResponse | null = null

export function clearCsrfToken() {
  csrfToken = null
}

async function loadCsrfToken(): Promise<CsrfTokenResponse> {
  let response: Response
  try {
    response = await fetch(new URL('/api/auth/csrf', window.location.origin), {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new ApiError(
      '通信に失敗しました。ネットワーク接続を確認して再試行してください。',
      0,
      'NETWORK_ERROR',
    )
  }

  let payload: ApiResponse<CsrfTokenResponse> | ErrorResponse | undefined
  try {
    payload = (await response.json()) as
      | ApiResponse<CsrfTokenResponse>
      | ErrorResponse
  } catch {
    payload = undefined
  }

  if (!response.ok || !payload?.success) {
    const error = payload as ErrorResponse | undefined
    throw new ApiError(
      error?.message ?? 'セキュリティ情報を取得できませんでした。',
      response.status,
      error?.code,
      error?.details,
    )
  }

  csrfToken = payload.data
  return csrfToken
}

async function executeRequest<T>(
  path: string,
  options: ApiRequestOptions,
  retryCsrf: boolean,
): Promise<T> {
  const { onUnauthorized, headers, ...requestOptions } = options
  const method = (requestOptions.method ?? 'GET').toUpperCase()
  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')

  if (!safeMethods.has(method)) {
    const currentCsrfToken = csrfToken ?? (await loadCsrfToken())
    requestHeaders.set(currentCsrfToken.headerName, currentCsrfToken.token)
  }
  if (requestOptions.body && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(new URL(path, window.location.origin), {
      ...requestOptions,
      credentials: requestOptions.credentials ?? 'same-origin',
      headers: requestHeaders,
    })
  } catch {
    throw new ApiError(
      '通信に失敗しました。ネットワーク接続を確認して再試行してください。',
      0,
      'NETWORK_ERROR',
    )
  }

  let payload: ApiResponse<T> | ErrorResponse | undefined
  try {
    payload = (await response.json()) as ApiResponse<T> | ErrorResponse
  } catch {
    payload = undefined
  }

  if (
    response.status === 403 &&
    (payload as ErrorResponse | undefined)?.code === 'CSRF_INVALID' &&
    retryCsrf
  ) {
    clearCsrfToken()
    await loadCsrfToken()
    return executeRequest<T>(path, options, false)
  }

  if (response.status === 401) {
    onUnauthorized?.()
  }

  if (!response.ok || !payload?.success) {
    const error = payload as ErrorResponse | undefined
    throw new ApiError(
      error?.message ??
        (response.status >= 500
          ? 'システムエラーが発生しました。しばらくしてから再試行してください。'
          : 'リクエストを完了できませんでした。'),
      response.status,
      error?.code,
      error?.details,
    )
  }

  return payload.data
}

export async function requestApi<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  return executeRequest<T>(path, options, true)
}
