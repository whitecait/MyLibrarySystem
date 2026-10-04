function formatTime(minutes) {
  const hours = String(Math.floor(minutes / 60)).padStart(2, '0')
  const remainder = String(minutes % 60).padStart(2, '0')
  return `${hours}:${remainder}`
}

export const PICKUP_SLOTS = Array.from({ length: 48 }, (_, index) => {
  const start = index * 30
  return `${formatTime(start)}-${formatTime(start + 30)}`
})

export function formatPickupSlot(value) {
  if (value === 'AM') return '上午（AM）'
  if (value === 'PM') return '下午（PM）'
  if (typeof value === 'string' && value.trim()) return value
  return '未知'
}
