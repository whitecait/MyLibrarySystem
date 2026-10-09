export function calendarMonth(month) {
  const [year, number] = month.split('-').map(Number)
  const length = new Date(Date.UTC(year, number, 0)).getUTCDate()
  const offset = (new Date(Date.UTC(year, number - 1, 1)).getUTCDay() + 6) % 7
  const cells = Array.from({ length: Math.ceil((offset + length) / 7) * 7 }, (_, index) => {
    const day = index - offset + 1
    return day < 1 || day > length ? null : `${month}-${String(day).padStart(2, '0')}`
  })
  return { from: `${month}-01`, to: `${month}-${length}`, weeks: Array.from({ length: cells.length / 7 }, (_, index) => cells.slice(index * 7, index * 7 + 7)) }
}

export function moveCalendarMonth(month, offset) {
  const [year, number] = month.split('-').map(Number)
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0, 7)
}
