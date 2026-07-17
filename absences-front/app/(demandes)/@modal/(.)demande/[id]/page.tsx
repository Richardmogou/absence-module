import AbsenceDetailPage from "../../../demande/[id]/page";
import Modal from "./modal";

export default async function InterceptedAbsencePage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ success?: string }>;
}) {
  return (
    <Modal>
      {/* We reuse the exact same server component that renders the page */}
      <AbsenceDetailPage params={params} searchParams={searchParams} isModal={true} />
    </Modal>
  );
}
