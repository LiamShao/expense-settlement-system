import { formatDateTime, formatYen } from './format'
import { buildQueryString, readPositiveInteger } from './query'

describe('format utilities', () => {
  it('金額を日本円で表示する', () => {
    expect(formatYen(13820)).toContain('13,820')
  })

  it('timezoneを持たないAPI日時をAsia/Tokyoとして表示する', () => {
    expect(formatDateTime('2026-07-18T10:30:00')).toContain('2026-07-18')
    expect(formatDateTime(null)).toBe('-')
  })
})

describe('query utilities', () => {
  it('空値を除外してquery stringを作る', () => {
    expect(
      buildQueryString({ keyword: '東京 出張', status: '', page: 0, size: 20 }),
    ).toBe('?keyword=%E6%9D%B1%E4%BA%AC+%E5%87%BA%E5%BC%B5&page=0&size=20')
  })

  it('0以上の整数だけを受け入れる', () => {
    expect(readPositiveInteger('2', 0)).toBe(2)
    expect(readPositiveInteger('-1', 0)).toBe(0)
    expect(readPositiveInteger('invalid', 20)).toBe(20)
  })
})
