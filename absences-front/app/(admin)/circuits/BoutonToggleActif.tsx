"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";

export function BoutonToggleActif({ circuitId, actif }: { circuitId: string, actif: boolean }) {
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const handleToggle = async () => {
    if (!confirm(actif ? "Voulez-vous vraiment désactiver ce circuit ?" : "Voulez-vous réactiver ce circuit ?")) return;
    try {
      setLoading(true);
      await apiClient.patch(`/api/v5/admin/circuits/${circuitId}/toggle-actif`);
      router.refresh();
    } catch (err) {
      console.error("Erreur lors du basculement :", err);
      alert("Une erreur est survenue.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button
      onClick={handleToggle}
      disabled={loading}
      variant={actif ? "outline" : "default"}
      size="sm"
      className={actif ? "border-orange-500 text-orange-600 hover:bg-orange-50" : "bg-green-600 hover:bg-green-700"}
    >
      {loading ? "..." : actif ? "Désactiver" : "Activer"}
    </Button>
  );
}
