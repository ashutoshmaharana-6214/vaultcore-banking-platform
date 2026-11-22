import { FeaturePlaceholder } from "../../shared/components/FeaturePlaceholder";

export function TransfersPage() {
  return <FeaturePlaceholder
    eyebrow="BANKING / PAYMENTS"
    title="Transfer money"
    description="Transfers are intentionally disabled in this milestone. No mock payment is submitted and no real banking API is invoked."
    tasks={["Secure the transfer endpoint with account ownership checks", "Introduce idempotency keys and request validation", "Build a confirmation flow with clear failure handling", "Test concurrent transfers and duplicate submissions"]}
  />;
}
