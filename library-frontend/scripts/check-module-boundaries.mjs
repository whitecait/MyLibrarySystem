import { readFile, readdir } from 'node:fs/promises'
import { dirname, extname, join, relative, resolve, sep } from 'node:path'
import process from 'node:process'

const sourceRoot = resolve('src')
const modulesRoot = join(sourceRoot, 'modules')
const allowedHttpRoots = [
  join(sourceRoot, 'platform', 'http'),
  join(sourceRoot, 'platform', 'contracts', 'generated'),
]
const sourceExtensions = new Set(['.js', '.mjs', '.jsx', '.ts', '.tsx', '.vue'])
const importPattern = /(?:from\s+|import\s*\()(['"])([^'"]+)\1/g
const directHttpPattern = /\b(?:fetch|XMLHttpRequest|axios\.(?:get|post|put|patch|delete))\b/
const violations = []

async function filesUnder(directory) {
  const result = []
  try {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) result.push(...(await filesUnder(path)))
      else if (sourceExtensions.has(extname(entry.name))) result.push(path)
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }
  return result
}

function moduleName(path) {
  const rel = relative(modulesRoot, path).split(sep)
  return rel[0] && rel[0] !== '..' ? rel[0] : null
}

for (const file of await filesUnder(sourceRoot)) {
  const text = await readFile(file, 'utf8')
  const owner = moduleName(file)
  for (const match of text.matchAll(importPattern)) {
    const specifier = match[2]
    const targetPath = specifier.startsWith('@/')
      ? resolve(sourceRoot, specifier.slice(2))
      : specifier.startsWith('.')
        ? resolve(dirname(file), specifier)
        : null
    if (!targetPath) continue
    const targetRelative = relative(modulesRoot, targetPath).split(sep)
    const targetModule = targetRelative[0]
    if (targetRelative[1] === 'internal' && targetModule !== owner) {
      violations.push(`${relative(sourceRoot, file)} imports ${targetModule}/internal`)
    }
  }
  if (!allowedHttpRoots.some((root) => file.startsWith(root + sep)) && directHttpPattern.test(text)) {
    violations.push(`${relative(sourceRoot, file)} performs HTTP outside platform/http`)
  }
}

if (violations.length) {
  console.error(violations.join('\n'))
  process.exitCode = 1
} else {
  console.log('Frontend module boundaries: OK')
}
