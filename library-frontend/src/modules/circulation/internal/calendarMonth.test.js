import { describe, expect, it } from 'vitest'
import { calendarMonth, moveCalendarMonth } from './calendarMonth'

describe('calendar month boundaries', () => {
  it('lays out a Monday-first month without inventing out-of-month dates', () => {
    const month = calendarMonth('2026-09')
    expect(month.from).toBe('2026-09-01')
    expect(month.to).toBe('2026-09-30')
    expect(month.weeks[0]).toEqual([null, '2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05', '2026-09-06'])
    expect(month.weeks.flat().filter(Boolean)).toHaveLength(30)
  })
  it('supports leap February and both directions across a year boundary', () => {
    expect(calendarMonth('2028-02').to).toBe('2028-02-29')
    expect(moveCalendarMonth('2026-12', 1)).toBe('2027-01')
    expect(moveCalendarMonth('2027-01', -1)).toBe('2026-12')
  })
})
