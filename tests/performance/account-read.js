// Authenticated read test. Seed a synthetic account and supply a test-only JWT.
// This uses a conservative default because server-side per-IP rate limiting is enabled.
import http from "k6/http";
import { check } from "k6";

const BASE_URL = (__ENV.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const isLocal = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL);
if (!isLocal && __ENV.APPROVED_STAGING_TEST !== "I_HAVE_PERMISSION") {
  throw new Error("Remote targets require explicit approval");
}
const token = __ENV.TEST_JWT;
const account = __ENV.TEST_ACCOUNT_NUMBER;
if (!token || !account || !/^[A-Za-z0-9_-]{8,24}$/.test(account)) {
  throw new Error("Set TEST_JWT and TEST_ACCOUNT_NUMBER for a synthetic, owned account");
}
const ratePerMinute = Number(__ENV.RATE_PER_MINUTE || "20");
if (!Number.isInteger(ratePerMinute) || ratePerMinute < 1 || ratePerMinute > 30) {
  throw new Error("RATE_PER_MINUTE must be 1..30; do not bypass API rate limits");
}

export const options = {
  scenarios: {
    authorized_account_read: {
      executor: "constant-arrival-rate",
      rate: ratePerMinute,
      timeUnit: "1m",
      duration: __ENV.DURATION || "2m",
      preAllocatedVUs: 5,
      maxVUs: 10
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.001"],
    "http_req_duration{endpoint:account}": ["p(95)<250"],
    checks: ["rate>0.999"]
  }
};

export default function () {
  const response = http.get(`${BASE_URL}/api/accounts/${account}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    timeout: "5s",
    tags: { endpoint: "account" }
  });
  check(response, { "authorized account read HTTP 200": r => r.status === 200 });
}
