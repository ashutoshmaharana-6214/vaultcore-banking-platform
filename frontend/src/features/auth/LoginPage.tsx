import { FeaturePlaceholder } from "../../shared/components/FeaturePlaceholder";

export function LoginPage() {
  return <FeaturePlaceholder
    eyebrow="SECURITY / AUTHENTICATION"
    title="Authentication"
    description="The backend's current main branch does not provide production-ready user authentication. Login will be enabled only when identity and session security are in place."
    tasks={["Implement and test Spring Security authentication", "Select a secure session or token transport strategy", "Add CSRF defenses where applicable and route protection", "Test login, logout, expiry and authorization boundaries"]}
  />;
}
