import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

// C1 scaffold-verification parameters. NOT approved business load:
// fixed low-rate health and known missing-title probes,
// executed through the frontend proxy (nginx -> backend).
const TARGET = __ENV.TARGET || 'http://frontend:80/api/health';
const MISSING_TARGET = __ENV.MISSING_TARGET || 'http://frontend:80/api/v1/titles/9223372036854775807';
const VUS = Number(__ENV.VUS || 3);
const SUCCESS_ITERS = Number(__ENV.SUCCESS_ITERS || 280);
const MISSING_ITERS = Number(__ENV.MISSING_ITERS || 20);
const HTTP_TIMEOUT_MS = Number(__ENV.HTTP_TIMEOUT_MS || 2000);
// IMPOSSIBLE=1 installs a threshold that can never pass; used only to prove
// that a threshold failure exits non-zero and leaves a failure report.
// Note: __ITER counts per VU, so exact request-class counts require separate
// scenarios; per-class totals are therefore exact by construction.
const IMPOSSIBLE = __ENV.IMPOSSIBLE === '1';

const successCount = new Counter('scaffold_requests_success');
const expected4xxCount = new Counter('scaffold_requests_expected_4xx');
const unexpected5xxCount = new Counter('scaffold_requests_unexpected_5xx');
const timeoutCount = new Counter('scaffold_requests_timeout');
// Connection-level failures without a response that are NOT timeouts
// (e.g. immediate network-unreachable): a distinct non-response class.
const networkErrorCount = new Counter('scaffold_requests_network_error');

const scenarios = {
  success: {
    executor: 'shared-iterations',
    vus: VUS,
    iterations: SUCCESS_ITERS,
    maxDuration: '15m',
  },
};
if (MISSING_ITERS > 0) {
  scenarios.missing = {
    executor: 'shared-iterations',
    vus: 1,
    iterations: MISSING_ITERS,
    maxDuration: '15m',
    exec: 'missingProbe',
  };
}

export const options = {
  scenarios,
  thresholds: IMPOSSIBLE
    ? { scaffold_requests_success: ['count<0'] }
    : {},
};

function classify(res) {
  if (res.status === 200) {
    successCount.add(1);
  } else if (res.status >= 400 && res.status < 500) {
    expected4xxCount.add(1);
  } else if (res.status >= 500) {
    unexpected5xxCount.add(1);
  } else if (res.status === 0) {
    const errText = `${res.error} ${res.error_code}`;
    if (/timed out|deadline|timeout/i.test(errText)) {
      timeoutCount.add(1);
    } else {
      networkErrorCount.add(1);
    }
  }
}

export default function () {
  classify(http.get(TARGET, { timeout: `${HTTP_TIMEOUT_MS}ms` }));
  sleep(0.02);
}

export function missingProbe() {
  classify(http.get(MISSING_TARGET, { timeout: `${HTTP_TIMEOUT_MS}ms` }));
  sleep(0.02);
}
