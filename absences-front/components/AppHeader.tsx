"use client";

import Image from "next/image";
import Link from "next/link";
import { useSession, signOut } from "next-auth/react";
import { Button } from "@/components/ui/button";
import { useState, useRef, useEffect } from "react";
import { UserCircle, ChevronDown, LogOut, Home, LayoutDashboard, FolderOpen, FileCheck, Users, Briefcase, Shield } from "lucide-react";

import { federatedLogout } from "@/app/actions/auth";

export default function AppHeader() {
  const { data: session } = useSession();
  const user = session?.user;

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSignOut = async () => {
    try {
      const url = await federatedLogout();
      window.location.href = url;
    } catch (err) {
      console.error("Erreur lors de la déconnexion fédérée :", err);
      signOut({ callbackUrl: "/connexion" });
    }
  };

  const NAV_LINKS = [
    { href: "/", label: "Accueil", icon: Home },
    { href: "/mon-espace", label: "Mon espace", icon: LayoutDashboard },
    { href: "/mes-demandes", label: "Mes demandes", icon: FolderOpen },
    { href: "/validation-file", label: "File validation", icon: FileCheck },
    { href: "/analyste-rh", label: "Analyste RH", icon: Users },
    { href: "/drh", label: "DRH", icon: Briefcase },
    { href: "/admin/dashboard", label: "Admin", icon: Shield },
  ];

  // Le grade de l'utilisateur (à adapter selon les données de session disponibles)
  const userGrade = "Collaborateur";

  return (
    <header className="sticky top-0 z-50 shadow-modal">
      {/* Fond global avec image et opacité (sans backdrop-blur) */}
      <div
        className="absolute inset-0 opacity-10"
        style={{
          backgroundImage: "url('/header/background_header.png')",
          backgroundSize: "cover",
          backgroundPosition: "center",
        }}
      ></div>
      <div className="absolute inset-0 bg-primary-500/95 -z-10" />

      <div className="relative z-10 mx-auto flex max-w-container items-stretch justify-between">
        
        {/* Section Logo avec fond blanc étendu vers la gauche */}
        <div className="relative flex items-center bg-white px-4 py-2 pr-6">
          {/* Extension du fond blanc vers l'infini à gauche */}
          <div className="absolute top-0 bottom-0 right-full w-[50vw] bg-white" />
          
          <Link href="/" className="relative z-10 flex items-center">
            <Image
              src="/header/logo_afb.png"
              alt="AFB — Banque"
              width={120}
              height={40}
              className="h-10 w-auto"
              priority
            />
          </Link>
        </div>

        {/* Zone utilisateur & Menu */}
        <div className="flex items-center px-6 py-3">
          {user ? (
            <div className="relative" ref={menuRef}>
              {/* Bouton de profil déclenchant le menu */}
              <button
                onClick={() => setIsMenuOpen(!isMenuOpen)}
                className="flex items-center gap-3 px-3 py-1.5 rounded-lg hover:bg-white/10 transition-colors focus:outline-none"
              >
                <div className="flex flex-col items-end">
                  <span className="text-sm font-semibold text-white leading-tight">
                    {user.name ?? user.email?.split('@')[0]}
                  </span>
                  <span className="text-xxs text-gold-300 font-ui uppercase tracking-wider">
                    {userGrade}
                  </span>
                </div>
                <UserCircle className="w-8 h-8 text-white/90" strokeWidth={1.5} />
                <ChevronDown className={`w-4 h-4 text-white/70 transition-transform ${isMenuOpen ? "rotate-180" : ""}`} />
              </button>

              {/* Menu déroulant */}
              {isMenuOpen && (
                <div className="absolute right-0 mt-2 w-64 bg-white rounded-xl shadow-xl border border-neutral-100 overflow-hidden py-2 animate-in fade-in slide-in-from-top-2">
                  <div className="px-4 py-3 border-b border-neutral-100 mb-2">
                    <p className="text-sm font-medium text-primary-500 truncate">{user.name ?? user.email}</p>
                    <p className="text-xs text-neutral-500 truncate">{user.email}</p>
                  </div>
                  
                  <nav className="flex flex-col px-2">
                    {NAV_LINKS.map((link) => (
                      <Link
                        key={link.href}
                        href={link.href}
                        onClick={() => setIsMenuOpen(false)}
                        className="flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium text-neutral-600 hover:text-primary-500 hover:bg-neutral-50 transition-colors"
                      >
                        <link.icon className="w-4 h-4" />
                        {link.label}
                      </Link>
                    ))}
                  </nav>

                  <div className="px-2 mt-2 pt-2 border-t border-neutral-100">
                    <button
                      onClick={() => {
                        setIsMenuOpen(false);
                        handleSignOut();
                      }}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium text-red-600 hover:bg-red-50 transition-colors"
                    >
                      <LogOut className="w-4 h-4" />
                      Déconnexion
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <Link href="/connexion">
              <Button
                size="sm"
                variant="outline"
                className="border-white/40 text-white hover:bg-white/10 text-xs h-8"
              >
                Connexion
              </Button>
            </Link>
          )}
        </div>
      </div>

      {/* Bande kente décorative sur toute la largeur */}
      <div
        className="absolute bottom-0 left-0 right-0 h-1 z-20"
        style={{
          background:
            "repeating-linear-gradient(90deg, #C41E22 0px, #C41E22 10px, #B8932A 10px, #B8932A 20px, #2C2C2C 20px, #2C2C2C 30px, #F5F5F5 30px, #F5F5F5 40px)",
        }}
      />
    </header>
  );
}
