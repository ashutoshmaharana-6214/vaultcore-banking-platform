import { FeaturePlaceholder } from "../../shared/components/FeaturePlaceholder";

export function TransactionsPage() {
  return <FeaturePlaceholder
    eyebrow="BANKING / ACTIVITY"
    title="Transaction history"
    description="This module will display transaction history returned by the backend after user authorization is implemented."
    tasks={["Connect a protected transactions API", "Show real history and transaction status", "Add pagination, filters and empty states", "Test access isolation across accounts"]}
  />;
}
