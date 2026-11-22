import { NavLink, Navigate, Route, Routes } from "react-router";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { AccountsPage } from "../features/accounts/AccountsPage";
import { TransfersPage } from "../features/payments/TransfersPage";
import { TransactionsPage } from "../features/transactions/TransactionsPage";
import { LoginPage } from "../features/auth/LoginPage";

const navItems = [
  { to: "/", label: "Overview", icon: "◈", end: true },
  { to: "/accounts", label: "Accounts", icon: "▤" },
  { to: "/transfers", label: "Transfers", icon: "⇄" },
  { to: "/transactions", label: "Transactions", icon: "▦" }
] as const;

export function App() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">V</div>
          <div>
            <strong>VaultCore</strong>
            <span>Banking Platform</span>
          </div>
        </div>
        <p className="nav-caption">WORKSPACE</p>
        <nav className="nav-links" aria-label="Primary navigation">
          {navItems.map(({ to, label, icon, ...rest }) => (
            <NavLink key={to} to={to} end={"end" in rest ? rest.end : false} className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
              <span aria-hidden="true" className="nav-icon">{icon}</span>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="sidebar-tag">Development preview</span>
          <p>Real banking actions stay disabled until authentication and authorization are integrated.</p>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">VAULTCORE / WEB APP</p>
            <strong>Secure banking, thoughtfully engineered.</strong>
          </div>
          <NavLink className="topbar-link" to="/login">Authentication status <span className="status-dot" aria-hidden="true" /> Not configured</NavLink>
        </header>
        <main id="main-content">
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/transfers" element={<TransfersPage />} />
            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer className="footer">VaultCore • Frontend foundation • Test environment only</footer>
      </div>
    </div>
  );
}
