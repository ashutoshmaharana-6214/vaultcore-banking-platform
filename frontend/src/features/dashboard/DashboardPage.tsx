import { useEffect, useState } from "react";
import { Link } from "react-router";
import { getBackendHealth } from "../../shared/api/client";

type BackendStatus = "checking" | "connected" | "unavailable";

export function DashboardPage() {
  const [status, setStatus] = useState<BackendStatus>("checking");

  useEffect(() => {
    const controller = new AbortController();
    getBackendHealth(controller.signal)
      .then((up) => { if (!controller.signal.aborted) setStatus(up ? "connected" : "unavailable"); })
      .catch(() => { if (!controller.signal.aborted) setStatus("unavailable"); });
    return () => controller.abort();
  }, []);

  const statusLabel = {
    checking: "Checking local backend…",
    connected: "Backend health: UP",
    unavailable: "Backend not reachable"
  }[status];

  return (
    <div className="page-content">
      <p className="eyebrow">YOUR BANKING WORKSPACE</p>
      <h1>Welcome to VaultCore<span className="accent">.</span></h1>
      <p className="lead">The foundation for a secure digital banking experience. This interface is a development preview — no balances or transactions are simulated.</p>
      <div className="hero-panel">
        <div className="hero-glow" aria-hidden="true" />
        <div className="hero-content">
          <span className="hero-kicker">APPLICATION STATUS</span>
          <h2>Frontend foundation<br />is ready.</h2>
          <p>Connect your Spring Boot services and finish authentication before enabling financial operations.</p>
          <span className={`health-indicator ${status}`} role="status"><span className="health-dot" />{statusLabel}</span>
        </div>
        <div className="hero-monogram" aria-hidden="true">V</div>
      </div>
      <div className="section-heading">
        <h2>Explore the modules</h2>
        <span>Integration pending</span>
      </div>
      <div className="modules-grid">
        <Link to="/accounts" className="module-card">
          <span className="module-icon" aria-hidden="true">▤</span>
          <h3>Accounts</h3>
          <p>Account details and balance display once protected APIs are ready.</p>
          <span className="module-action">Open module →</span>
        </Link>
        <Link to="/transfers" className="module-card">
          <span className="module-icon" aria-hidden="true">⇄</span>
          <h3>Transfers</h3>
          <p>Validated transfers with idempotency and authorization.</p>
          <span className="module-action">Open module →</span>
        </Link>
        <Link to="/transactions" className="module-card">
          <span className="module-icon" aria-hidden="true">▦</span>
          <h3>Transactions</h3>
          <p>Browse verified history from the banking backend.</p>
          <span className="module-action">Open module →</span>
        </Link>
      </div>
    </div>
  );
}
