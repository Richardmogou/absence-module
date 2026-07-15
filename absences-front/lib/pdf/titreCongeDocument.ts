/**
 * Génère le HTML pleine fidélité du « Titre de congé » côté navigateur (impression PDF).
 *
 * Inspiré de Titre_de_conge.docx : en-tête = bannière image pleine largeur (logo + motif
 * africain + bande Kente intégrés), pied = bande Kente + mention, filigrane = image diagonale.
 *
 * Choix clés pour l'impression navigateur :
 *  - En-tête/pied placés dans <thead>/<tfoot> d'un tableau : tous les navigateurs les
 *    RÉPÈTENT sur chaque page à l'impression ET réservent leur espace (le contenu ne les
 *    chevauche jamais, y compris en multi-pages). Plus fiable que `position: fixed` + offsets.
 *  - On utilise des <img> (et non des `background` CSS) car Chrome n'imprime pas les
 *    arrière-plans par défaut ; `print-color-adjust: exact` force malgré tout les couleurs.
 *  - Images en URL absolue (origin) car le document est écrit dans une iframe about:blank.
 */

export interface TitreCongeData {
  issuing_department: string;
  absence_type: string;
  employee_full_name: string;
  employee_matricule: string;
  employee_position?: string;
  employee_department?: string;
  direct_manager_name?: string;
  date_debut: string;
  date_fin: string;
  nombre_jours: number | string;
  lieu_jouissance?: string;
  date_reprise: string;
  document_location: string;
  document_date: string;
  hr_signatory_title: string;
  hr_signatory_name: string;
  analyste_rh_name: string;
  generation_timestamp: string;
}

const esc = (v: unknown): string =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!),
  );

const or = (v: string | undefined, fallback = "Non renseigné"): string =>
  v && v.trim() ? esc(v) : fallback;

export function buildTitreCongeHtml(data: TitreCongeData, origin: string): string {
  const entete = `${origin}/documents/entete.png`;
  const kente = `${origin}/documents/kente.png`;
  const filigrane = `${origin}/icon_afb.png`;

  return `<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8"/>
  <title>Titre de congé — ${esc(data.employee_full_name)}</title>
  <style>
    /* Marge de page : côtés/haut à 0 (en-tête bord à bord, collé en haut),
       bas réservé pour le pied de page FIXE. Le corps récupère ses marges
       latérales via le padding de la cellule tbody. */
    @page { size: A4; margin: 0 0 14mm 0; }

    * { box-sizing: border-box; }

    html, body {
      margin: 0;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    body { font-family: Arial, Helvetica, sans-serif; font-size: 14px; color: #000; }

    /* Tableau « page » : thead = en-tête répété sur chaque page (espace réservé auto). */
    table.page { width: 100%; border-collapse: collapse; }
    table.page > thead > tr > td { border: none; padding: 0; }
    table.page > tbody > tr > td { border: none; padding: 10mm 15mm 8mm; }

    /* En-tête : bannière Afriland pleine largeur (logo + masques + Kente intégrés). */
    .doc-header img { display: block; width: 100%; }

    /* Pied de page FIXE : collé en bas de chaque page, bord à bord.
       Bande Kente à hauteur réduite (2mm) + mention. */
    .doc-footer {
      position: fixed; bottom: 0; left: 0; right: 0;
    }
    .doc-footer img { display: block; width: 100%; height: 2mm; }
    .doc-footer .note {
      font-size: 9px; font-style: italic; color: #555; line-height: 1.4;
      text-align: center; padding: 1.5mm 6mm 2mm;
    }

    /* Filigrane : image centrée, atténuée et en arrière-plan. */
    .filigrane {
      position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
      width: 145mm; z-index: -1;
      opacity: 0.1; /* Transparence pour effet filigrane */
      filter: grayscale(100%);
    }

    .content { position: relative; }

    .title { text-align: center; margin: 4mm 0 12mm 0; }
    .title h1 { font-size: 18px; font-weight: normal; margin: 0; }
    .title h2 { font-size: 20px; font-weight: bold; margin-top: 10px; margin-bottom: 0; }

    .section { margin-top: 25px; }
    .section p { line-height: 1.6; margin: 0 0 12px 0; text-align: justify; }

    .info-table { border: none; width: 100%; border-collapse: collapse; }
    .info-table td { border: none; padding: 3px 0; }
    .info-table td.label { width: 180px; font-weight: bold; }
    .info-table td.sep { width: 20px; }

    .signature .sig-title { font-size: 12px; }
    .signature .sig-name { font-size: 14px; font-weight: bold; }
  </style>
</head>
<body>

  <img class="filigrane" src="${filigrane}" alt=""/>

  <!-- Pied de page fixe (collé en bas de chaque page imprimée) -->
  <div class="doc-footer">
    <img src="${kente}" alt=""/>
    <div class="note">
      Document généré automatiquement par le système INTRA-ABS - Non modifiable
      <br/>
      Généré le ${esc(data.generation_timestamp)}
    </div>
  </div>

  <table class="page">
    <thead>
      <tr><td>
        <div class="doc-header"><img src="${entete}" alt="En-tête Afriland First Bank"/></div>
      </td></tr>
    </thead>

    <tbody>
      <tr><td>
        <div class="content">
          <div class="title">
            <h1>${esc(data.issuing_department)}</h1>
            <h2>${esc(data.absence_type)}</h2>
          </div>

          <div class="section">
            <table class="info-table">
              <tr><td class="label">Nom et prénom</td><td class="sep">:</td><td>${esc(data.employee_full_name)}</td></tr>
              <tr><td class="label">Matricule</td><td class="sep">:</td><td>${esc(data.employee_matricule)}</td></tr>
              <tr><td class="label">Poste</td><td class="sep">:</td><td>${or(data.employee_position)}</td></tr>
              <tr><td class="label">Département</td><td class="sep">:</td><td>${or(data.employee_department)}</td></tr>
              <tr><td class="label">Manager direct</td><td class="sep">:</td><td>${or(data.direct_manager_name)}</td></tr>
              <tr><td class="label">Motif / Type</td><td class="sep">:</td><td>${esc(data.absence_type)}</td></tr>
            </table>
          </div>

          <div class="section" style="margin-top: 30px;">
            <p>
              La Direction des Ressources Humaines atteste par le présent document que
              <strong>l'employé(e) susmentionné(e)</strong> est officiellement autorisé(e) à suspendre
              temporairement son activité professionnelle pour motif de
              <strong>${esc(data.absence_type)}</strong>.
            </p>
            <p>
              Cette autorisation d'absence couvre la période allant du
              <strong>${esc(data.date_debut)}</strong> au <strong>${esc(data.date_fin)}</strong> inclus
              (représentant un total de <strong>${esc(data.nombre_jours)} jours calendaires</strong>), durant laquelle
              l'employé(e) a déclaré jouir de ce congé à la localisation suivante :
              <strong>${or(data.lieu_jouissance, "Non spécifié")}</strong>, avant une reprise formelle des
              fonctions fixée au <strong>${esc(data.date_reprise)}</strong> aux horaires habituels de travail.
            </p>
            <p>
              En cas de force majeure nécessitant une modification de ces dates, l'employé(e) est tenu(e)
              d'en informer immédiatement sa hiérarchie ainsi que la Direction des Ressources Humaines.
            </p>
            <p>
              Nous certifions que cette demande a suivi l'intégralité du circuit d'approbation réglementaire
              de l'entreprise. Elle a été examinée et validée par le supérieur hiérarchique direct ainsi que
              par les autorités compétentes, conformément aux dispositions internes en vigueur.
            </p>
            <p>
              En foi de quoi, le présent titre de congé est délivré pour servir et valoir ce que de droit.
            </p>
          </div>

          <div class="signature" style="margin-top: 40px;">
            <p style="margin: 0 0 20px; text-align: right;">Fait à ${esc(data.document_location)}, le ${esc(data.document_date)}</p>
            <table style="width: 100%; border: none; border-collapse: collapse;">
              <tr>
                <td style="width: 50%; text-align: left; vertical-align: top; border: none; padding: 0;">
                  <p class="sig-title" style="margin: 0;">L'Analyste RH</p>
                  <p class="sig-name" style="margin-top: 40px;">${esc(data.analyste_rh_name)}</p>
                </td>
                <td style="width: 50%; text-align: right; vertical-align: top; border: none; padding: 0;">
                  <p class="sig-title" style="margin: 0;">${esc(data.hr_signatory_title)}</p>
                  <p class="sig-name" style="margin-top: 40px;">${esc(data.hr_signatory_name)}</p>
                </td>
              </tr>
            </table>
          </div>
        </div>
      </td></tr>
    </tbody>
  </table>

</body>
</html>`;
}
