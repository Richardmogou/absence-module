"use client";

import { useState, useEffect } from "react";
import apiClient from "@/lib/api/client";

export function BackupSelector({ 
  value, 
  onChange,
  disabled = false,
  placeholder = "— Facultatif : rechercher un Back-up —"
}: { 
  value?: string;
  onChange: (val: string) => void;
  disabled?: boolean;
  placeholder?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [backups, setBackups] = useState<Array<{id: string, firstName: string, lastName: string}>>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient
      .get("/api/v5/referentiel/backup-possibles")
      .then((r) => setBackups(r.data.pairs || []))
      .catch(() => console.error("Impossible de charger la liste des collègues backup."))
      .finally(() => setLoading(false));
  }, []);

  const filtered = backups.filter(b => 
    `${b.lastName} ${b.firstName}`.toLowerCase().includes(search.toLowerCase()) ||
    b.id.toLowerCase().includes(search.toLowerCase())
  );

  const selected = backups.find(b => b.id === value);

  if (loading) {
    return (
      <div className="h-10 w-full rounded border border-neutral-300 bg-neutral-50 px-3 py-2 text-sm text-neutral-400 flex items-center">
        Chargement des collègues…
      </div>
    );
  }

  return (
    <div className="relative w-full">
      <div 
        className={`min-h-10 w-full rounded border border-neutral-300 bg-white px-3 py-2 text-sm text-primary-500 flex items-center justify-between focus-within:ring-2 focus-within:ring-secondary-500 ${disabled ? 'opacity-50 cursor-not-allowed bg-neutral-50' : 'cursor-pointer'}`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
      >
        <span>
          {selected ? `${selected.lastName} ${selected.firstName}` : <span className="text-neutral-400">{placeholder}</span>}
        </span>
        <span className="text-neutral-400 text-xs">▼</span>
      </div>

      {isOpen && !disabled && (
        <div className="absolute z-10 mt-1 w-full rounded-md border border-neutral-200 bg-white shadow-lg">
          <div className="p-2 border-b border-neutral-100">
            <input 
              type="text" 
              autoFocus
              className="w-full text-sm outline-none bg-neutral-50 rounded px-2 py-1.5" 
              placeholder="Rechercher par nom..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <ul className="max-h-60 overflow-auto py-1 text-sm">
            <li 
              className={`px-3 py-2 cursor-pointer hover:bg-neutral-50 ${!value ? "bg-neutral-50 font-semibold text-secondary-600" : "text-neutral-500"}`}
              onClick={() => {
                onChange("");
                setIsOpen(false);
                setSearch("");
              }}
            >
              — Aucun Back-up —
            </li>
            {filtered.length === 0 ? (
              <li className="px-3 py-2 text-neutral-400 text-center">Aucun collègue trouvé</li>
            ) : (
              filtered.map((b) => (
                <li 
                  key={b.id} 
                  className={`px-3 py-2 cursor-pointer hover:bg-neutral-50 ${value === b.id ? "bg-neutral-50 font-semibold text-secondary-600" : "text-neutral-700"}`}
                  onClick={() => {
                    onChange(b.id);
                    setIsOpen(false);
                    setSearch("");
                  }}
                >
                  {b.lastName} {b.firstName}
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
