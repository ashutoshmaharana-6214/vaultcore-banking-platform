import { FeaturePlaceholder } from "../../shared/components/FeaturePlaceholder";

export function AccountsPage() {
  return <FeaturePlaceholder
    eyebrow="BANKING / ACCOUNTS"
    title="Your accounts"
    description="Account information will be loaded only after secure authentication and account ownership checks are in place."
    tasks={["Finish authentication and authorization", "Connect typed account APIs", "Show real balances with loading and error states", "Add accessibility and integration tests"]}
  />;
}
