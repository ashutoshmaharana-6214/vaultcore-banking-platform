import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { SharedArray } from "k6/data";

// Runs ONLY on an authorized synthetic-data environment.
// Never bypass authentication, account ownership or API rate limiting.
const base = (__ENV.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const local = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(base);
if (!local && __ENV.APPROVED_STAGING_TEST !== "I_HAVE_PERMISSION") {
  throw new Error("Remote benchmark requires APPROVED_STAGING_TEST=I_HAVE_PERMISSION");
}
const vus = Number(__ENV.VUS || "2");
const maximum = __ENV.HIGH_SCALE_APPROVED === "I_ACCEPT_THE_LOAD_AND_COST" && !local
  ? 10000 : (local ? 50 : 100);
if (!Number.isInteger(vus) || vus < 1 || vus > maximum) {
  throw new Error("VUS must be an integer within the authorized limit: " + maximum);
}
const token = __ENV.TEST_JWT;
const runId = __ENV.RUN_ID;
if (!token || !runId || !/^[A-Za-z0-9._:-]{1,40}$/.test(runId)) {
  throw new Error("Provide TEST_JWT and unique RUN_ID via local environment.");
}

const fixtures = new SharedArray("pre-funded isolated accounts", () => {
  const raw = JSON.parse(open(__ENV.TRANSFER_FIXTURES || "./transfer-fixtures.example.json"));
  if (!Array.isArray(raw) || raw.length < vus) {
    throw new Error("At least VUS verified, funded sandbox account pairs are required.");
  }
  const seen = new Set();
  for (const row of raw) {
    if (!row || !/^[A-Za-z0-9_-]{8,24}$/.test(row.fromAccount)
        || !/^[A-Za-z0-9_-]{8,24}$/.test(row.toAccount)
        || row.fromAccount === row.toAccount
        || seen.has(row.fromAccount) || seen.has(row.toAccount)) {
      throw new Error("Each account in the fixture must be valid and unique.");
    }
    seen.add(row.fromAccount);
    seen.add(row.toAccount);
  }
  return raw;
});

export const options = {
  scenarios: {
    independent_transfers: {
      executor: "per-vu-iterations",
      vus,
      iterations: 1,
      maxDuration: "2m"
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.001"],
    "http_req_duration{operation:transfer}": ["p(95)<50"],
    checks: ["rate>0.999"]
  }
};

export default function () {
  const index = exec.vu.idInTest - 1;
  const pair = fixtures[index];
  const response = http.post(base + "/api/transfers", JSON.stringify({
    fromAccount: pair.fromAccount,
    toAccount: pair.toAccount,
    amount: 1.00
  }), {
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer " + token,
      "Idempotency-Key": "benchmark-" + runId + "-" + index
    },
    tags: { operation: "transfer" },
    timeout: "10s"
  });
  check(response, {
    "committed transfer returns HTTP 201": r => r.status === 201,
    "has completed transaction reference": r => {
      if (r.status !== 201) return false;
      try {
        const body = r.json();
        return body.status === "COMPLETED" && typeof body.reference === "string";
      } catch (_) {
        return false;
      }
    }
  });
}
