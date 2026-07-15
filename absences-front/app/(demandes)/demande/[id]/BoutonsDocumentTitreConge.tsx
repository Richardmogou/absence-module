"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Download, Printer, Loader2 } from "lucide-react";
import { buildTitreCongeHtml, type TitreCongeData } from "@/lib/pdf/titreCongeDocument";

/**
 * Deux voies de téléchargement du Titre de congé :
 *  - « serveur »   : PDF officiel généré par le backend (Thymeleaf + openhtmltopdf), stocké sur MinIO.
 *  - « navigateur » : rendu pleine fidélité de la maquette imprimé en PDF par le navigateur
 *    (dégradés, opacity, en-tête/pied fixes répétés — non supportés par openhtmltopdf).
 */
export function BoutonsDocumentTitreConge({
  minioUrl,
  data,
}: {
  minioUrl?: string;
  data: TitreCongeData;
}) {
  const [busy, setBusy] = useState(false);

  function genererViaNavigateur() {
    setBusy(true);
    const now = new Date();
    const html = buildTitreCongeHtml(
      {
        ...data,
        document_date: now.toLocaleDateString("fr-FR"),
        generation_timestamp: now.toLocaleString("fr-FR"),
      },
      window.location.origin,
    );

    const iframe = document.createElement("iframe");
    Object.assign(iframe.style, {
      position: "fixed", right: "0", bottom: "0", width: "0", height: "0", border: "0",
    });
    document.body.appendChild(iframe);

    const cw = iframe.contentWindow;
    if (!cw) { iframe.remove(); setBusy(false); return; }

    cw.document.open();
    cw.document.write(html);
    cw.document.close();

    const attendreImages = () =>
      Promise.all(
        Array.from(cw.document.images).map((img) =>
          img.complete
            ? Promise.resolve()
            : new Promise<void>((res) => { img.onload = img.onerror = () => res(); }),
        ),
      );

    attendreImages()
      .then(() => {
        cw.focus();
        cw.print();
      })
      .finally(() => {
        setTimeout(() => { iframe.remove(); setBusy(false); }, 500);
      });
  }

  return (
    <div className="flex flex-col gap-2">
      {minioUrl && (
        <Button asChild className="w-full bg-gold-600 hover:bg-gold-700 text-white shadow-md rounded-xl">
          <a href={minioUrl} target="_blank" rel="noopener noreferrer">
            <Download size={16} /> PDF officiel (serveur)
          </a>
        </Button>
      )}
      <Button
        onClick={genererViaNavigateur}
        disabled={busy}
        variant="outline"
        className="w-full rounded-xl border-gold-300 text-gold-800 hover:bg-gold-50"
      >
        {busy ? <Loader2 size={16} className="animate-spin" /> : <Printer size={16} />}
        {busy ? "Préparation…" : "Générer via le navigateur"}
      </Button>
    </div>
  );
}
