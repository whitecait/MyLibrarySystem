// Current reservation input values. Historical values remain readable via formatPickupSlot.
export const PICKUP_SLOTS = Object.freeze([
  '09:00-10:30',
  '10:30-12:00',
  '13:00-14:30',
  '14:30-16:00',
  '16:00-17:00',
])

export function formatPickupSlot(value) {
  if (value === 'AM') return '上午（AM）'
  if (value === 'PM') return '下午（PM）'
  if (typeof value === 'string' && value.trim()) return value
  return '未知'
}
