import { PICKUP_SLOTS } from './pickupSlots'

const libraryTime = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

// Defaults are suggestions. Reservation eligibility still comes from the server.
export function getLibraryDate(now = new Date()) {
  const parts = Object.fromEntries(libraryTime.formatToParts(now).map(part => [part.type, part.value]))
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function getReservationDefaults(now = new Date()) {
  const parts = Object.fromEntries(libraryTime.formatToParts(now).map(part => [part.type, part.value]))
  const tomorrow = new Date(Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day) + 1))
  const minutes = Number(parts.hour) * 60 + Number(parts.minute)
  const pickupSlot = PICKUP_SLOTS.find(slot => {
    const [hour, minute] = slot.split('-')[1].split(':').map(Number)
    return minutes < hour * 60 + minute
  }) ?? PICKUP_SLOTS[0]
  return { reservationDate: tomorrow.toISOString().slice(0, 10), pickupSlot }
}
