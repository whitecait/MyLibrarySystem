import { defineComponent, h } from 'vue'

function inlineNodes(value, depth = 0) {
  if (depth >= 8) return [value]
  const nodes = []
  let cursor = 0
  let plain = ''
  const flush = () => {
    if (plain) nodes.push(plain)
    plain = ''
  }

  while (cursor < value.length) {
    if (value[cursor] === '`') {
      const end = value.indexOf('`', cursor + 1)
      if (end > cursor + 1) {
        flush()
        nodes.push(h('code', value.slice(cursor + 1, end)))
        cursor = end + 1
        continue
      }
    }

    let matched = false
    for (const [marker, tag] of [['**', 'strong'], ['__', 'strong'], ['~~', 'del'], ['*', 'em'], ['_', 'em']]) {
      if (!value.startsWith(marker, cursor)) continue
      const end = value.indexOf(marker, cursor + marker.length)
      if (end <= cursor + marker.length) continue
      flush()
      nodes.push(h(tag, inlineNodes(value.slice(cursor + marker.length, end), depth + 1)))
      cursor = end + marker.length
      matched = true
      break
    }
    if (!matched) {
      plain += value[cursor]
      cursor += 1
    }
  }

  flush()
  return nodes
}

function splitCells(line) {
  let value = line.trim()
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|')) value = value.slice(0, -1)
  const cells = []
  let cell = ''
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] === '\\' && value[index + 1] === '|') {
      cell += '|'
      index += 1
    } else if (value[index] === '|') {
      cells.push(cell.trim())
      cell = ''
    } else {
      cell += value[index]
    }
  }
  cells.push(cell.trim())
  return cells
}

function isTableSeparator(line) {
  const cells = splitCells(line)
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell))
}

function isBlockStart(lines, index) {
  const line = lines[index] ?? ''
  return /^\s{0,3}#{1,6}\s+/.test(line)
    || /^\s*```/.test(line)
    || /^\s*(?:[-*+]\s+|\d+[.)]\s+)/.test(line)
    || /^\s*>/.test(line)
    || (line.includes('|') && index + 1 < lines.length && isTableSeparator(lines[index + 1]))
}

function tableNode(headers, rows) {
  return h('div', { class: 'markdown-table-scroll' }, [
    h('table', { class: 'markdown-table' }, [
      h('thead', [h('tr', headers.map((cell) => h('th', inlineNodes(cell))))]),
      h('tbody', rows.map((row) => h('tr', headers.map((_, index) => h('td', inlineNodes(row[index] ?? '')))))),
    ]),
  ])
}

function renderBlocks(source) {
  const lines = String(source ?? '').replace(/\r\n?/g, '\n').split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index += 1
      continue
    }

    const fence = line.match(/^\s*```([^`]*)$/)
    if (fence) {
      const code = []
      index += 1
      while (index < lines.length && !/^\s*```\s*$/.test(lines[index])) code.push(lines[index++])
      if (index < lines.length) index += 1
      blocks.push(h('pre', [h('code', code.join('\n'))]))
      continue
    }

    const heading = line.match(/^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/)
    if (heading) {
      blocks.push(h(`h${heading[1].length}`, inlineNodes(heading[2])))
      index += 1
      continue
    }

    if (line.includes('|') && index + 1 < lines.length && isTableSeparator(lines[index + 1])) {
      const headers = splitCells(line)
      const rows = []
      index += 2
      while (index < lines.length && lines[index].includes('|') && lines[index].trim()) rows.push(splitCells(lines[index++]))
      blocks.push(tableNode(headers, rows))
      continue
    }

    const list = line.match(/^\s*([-*+]|\d+[.)])\s+/)
    if (list) {
      const ordered = /^\d/.test(list[1])
      const items = []
      while (index < lines.length) {
        const item = lines[index].match(/^\s*([-*+]|\d+[.)])\s+(.*)$/)
        if (!item || /^\d/.test(item[1]) !== ordered) break
        items.push(h('li', inlineNodes(item[2])))
        index += 1
      }
      blocks.push(h(ordered ? 'ol' : 'ul', items))
      continue
    }

    if (/^\s*>/.test(line)) {
      const quote = []
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ''))
      blocks.push(h('blockquote', [h('p', inlineNodes(quote.join(' ')))]))
      continue
    }

    if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
      blocks.push(h('hr'))
      index += 1
      continue
    }

    const paragraph = [line]
    index += 1
    while (index < lines.length && lines[index].trim() && !isBlockStart(lines, index)) paragraph.push(lines[index++])
    blocks.push(h('p', inlineNodes(paragraph.join('\n'))))
  }

  return blocks
}

export default defineComponent({
  name: 'SafeMarkdown',
  props: { source: { type: String, default: '' } },
  setup(props) {
    return () => h('div', { class: 'safe-markdown' }, renderBlocks(props.source))
  },
})
