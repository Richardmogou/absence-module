import { z } from "zod";

export const absenceBaseSchema = z
  .object({
    dateDebut: z.string().min(1, "La date de début est obligatoire"),
    dateFin: z.string().min(1, "La date de fin est obligatoire"),
    motif: z.string().max(500).optional(),
    backupIdentifiantExterne: z.string().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de fin doit être après la date de début",
    path: ["dateFin"],
  });

/**
 * Schéma congé annuel — employeId et motif supprimés :
 * le backend lit l'identifiant et le réseau directement depuis le JWT Keycloak.
 */
export const congeAnnuelSchema = z
  .object({
    dateDebut:              z.string().min(1, "La date de début est obligatoire"),
    dateFin:                z.string().min(1, "La date de fin est obligatoire"),
    backupIdentifiantExterne: z.string().min(1, "Le Back-up est obligatoire"),
    numeroFraction:         z.number().int().min(1).optional(),
    estPremiereFraction:    z.boolean().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de fin doit être après la date de début",
    path: ["dateFin"],
  });

export const congeMaladieSchema = absenceBaseSchema.and(
  z.object({ justificatif: z.string().optional() })
);

export const permissionSchema = z.object({
  dateDebut: z.string().min(1, "La date est obligatoire"),
  dateFin: z.string().min(1, "La date est obligatoire"),
  motif: z.string().min(1, "Le motif est obligatoire").max(500),
  backupIdentifiantExterne: z.string().optional(),
});

export const missionLongueSchema = z
  .object({
    dateDebut:   z.string().min(1, "La date de début est obligatoire"),
    dateFin:     z.string().min(1, "La date de fin est obligatoire"),
    nombreJours: z.number().min(15, "La mission doit durer au minimum 15 jours"),
    motif:       z.string().max(500).optional(),
    backupIdentifiantExterne: z.string().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de fin doit être après la date de début",
    path: ["dateFin"],
  });

export const congeMaterniteSchema = absenceBaseSchema;

export type CongeAnnuelData    = z.infer<typeof congeAnnuelSchema>;
export type CongeMaladieData = z.infer<typeof congeMaladieSchema>;
export type PermissionData = z.infer<typeof permissionSchema>;
export type MissionLongueData = z.infer<typeof missionLongueSchema>;
export type CongeMaterniteData = z.infer<typeof congeMaterniteSchema>;
