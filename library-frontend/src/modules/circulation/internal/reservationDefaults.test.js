import { describe, expect, it } from 'vitest'
import { getReservationDefaults } from './reservationDefaults'

describe('Shanghai reservation suggestions', () => {
  it.each([
    ['08:00', '00:00', '09:00-10:30'],
    ['09:00', '01:00', '09:00-10:30'],
    ['10:29', '02:29', '09:00-10:30'],
    ['10:30', '02:30', '10:30-12:00'],
    ['11:00', '03:00', '10:30-12:00'],
    ['12:00', '04:00', '13:00-14:30'],
    ['12:30', '04:30', '13:00-14:30'],
    ['13:00', '05:00', '13:00-14:30'],
    ['14:30', '06:30', '14:30-16:00'],
    ['16:00', '08:00', '16:00-17:00'],
    ['16:59', '08:59', '16:00-17:00'],
    ['17:00', '09:00', '09:00-10:30'],
    ['18:00', '10:00', '09:00-10:30'],
  ])('at %s (%s UTC) suggests %s tomorrow', (_localTime, utcTime, pickupSlot) => {
    expect(getReservationDefaults(new Date(`2026-10-09T${utcTime}:00Z`))).toEqual({ reservationDate: '2026-10-10', pickupSlot })
  })

  it.each([
    ['2026-10-09T16:30:00Z', '2026-10-11'],
    ['2026-12-31T04:00:00Z', '2027-01-01'],
    ['2028-02-28T04:00:00Z', '2028-02-29'],
  ])('uses the Shanghai date and calendar rollover for %s', (now, tomorrow) => {
    expect(getReservationDefaults(new Date(now)).reservationDate).toBe(tomorrow)
  })
})
