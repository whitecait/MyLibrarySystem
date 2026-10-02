// Short preparation check; this is not a formal performance-protocol attempt.
import { setup as registerReaders } from './warmup.js'
import { setup as loginReaders, writes, audit, paymentChain } from './steady.js'
export { handleSummary } from '../lib/common.js'
export const options = { vus: 1, iterations: 1, thresholds: { unexpected_failures: ['count==0'] } }
export function setup() { registerReaders(); return loginReaders() }
export default function (data) { writes(data); audit(data); paymentChain(data) }
