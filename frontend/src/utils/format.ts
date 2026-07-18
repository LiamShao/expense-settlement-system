const yenFormatter = new Intl.NumberFormat('ja-JP', {
  style: 'currency',
  currency: 'JPY',
  maximumFractionDigits: 0,
})

const dateTimeFormatter = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatYen(value: number): string {
  return yenFormatter.format(value)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }

  const hasOffset = /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
  const date = new Date(hasOffset ? value : `${value}+09:00`)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }

  return dateTimeFormatter.format(date).replace(/\//g, '-')
}
