import type { ValidationErrorDetail } from './types'

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
  credential?: string | null
  onUnauthorized?: () => void
}

export async function requestApi<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { credential, onUnauthorized, headers, ...requestOptions } = options
  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')

  if (credential) {
    requestHeaders.set('Authorization', credential)
  }
  if (requestOptions.body && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(new URL(path, window.location.origin), {
      ...requestOptions,
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
