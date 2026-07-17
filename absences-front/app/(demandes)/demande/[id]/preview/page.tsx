import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { serverApiClient } from "@/lib/api/server-client";
import ConfirmSubmitForm from "./_ConfirmSubmitForm";
import { AlertTriangle } from "lucide-react";

interface Demande {
  type: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number;
}

interface PreviewData {
  demande: Demande;
  doublonDetecte: boolean;
  circuitDetermine: { nom: string } | null;
}

async function getPreview(id: string): Promise<PreviewData> {
  const api = await serverApiClient();
  const { data } = await api.get(`/api/v5/demandes/${id}/preview`);
  return data;
}

function isDgConditionnelApplicable(preview: PreviewData): boolean {
  // Mirrors AbsenceServiceImpl.nomCircuitTechnique(): circuit nommé "Agent" (insensible à la casse)
  // ET type MISSION_LONGUE → DGConditionnelService.necessiteInjection() retournera true.
  return (
    (preview.circuitDetermine?.nom?.toUpperCase().includes("AGENT") ?? false) &&
    preview.demande.type === "MISSION_LONGUE"
  );
}

export default async function PreviewPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const preview = await getPreview(id);
  const { demande, circuitDetermine } = preview;
  const showDgWarning = isDgConditionnelApplicable(preview);

  return (
    <Card className="max-w-lg mx-auto">
      <CardHeader>
        <CardTitle>Aperçu avant soumission</CardTitle>
      </CardHeader>

      <CardContent className="flex flex-col gap-4 text-sm text-primary-500">
        <p className="text-neutral-500">
          Vérifiez les informations avant de soumettre définitivement votre demande.
        </p>

        <div className="rounded border border-neutral-200 p-4 flex flex-col gap-2">
          <Row label="Type" value={demande.type} />
          <Row label="Date de début" value={demande.dateDebut} />
          {demande.dateFin && <Row label="Date de fin" value={demande.dateFin} />}
          <Row label="Nombre de jours" value={String(demande.nombreJours)} />
          {circuitDetermine && (
            <Row label="Circuit de validation" value={circuitDetermine.nom} />
          )}
        </div>

        {showDgWarning && (
          <div className="flex items-start gap-2 rounded border border-amber-300 bg-amber-50 p-3 text-amber-800">
            <AlertTriangle size={16} className="mt-0.5 flex-shrink-0 text-amber-600" aria-hidden="true" />
            <p className="text-sm">
              <span className="font-semibold">Étape supplémentaire :</span>{" "}
              validation du Directeur Général (requise car Mission longue)
            </p>
          </div>
        )}
      </CardContent>

      <CardFooter>
        <ConfirmSubmitForm id={id} doublonDetecte={preview.doublonDetecte} />
      </CardFooter>
    </Card>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-neutral-100 pb-2 last:border-0 last:pb-0">
      <span className="font-medium text-neutral-600">{label}</span>
      <span>{value}</span>
    </div>
  );
}
