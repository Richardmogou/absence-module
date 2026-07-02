import Image from "next/image";
import Link from "next/link";
import { auth } from "@/auth";
import { redirect } from "next/navigation";

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = await auth();
  if (!session?.roles?.includes("ADMIN_RH")) {
    redirect("/non-autorise");
  }
  return (
    <div className="flex flex-col flex-1">

      {/* ── Bandeau hero admin ── */}
      <div className="relative overflow-hidden" style={{ minHeight: "120px" }}>
        <Image
          src="/Image_Afrique3_resize.png"
          alt=""
          aria-hidden="true"
          fill
          sizes="100vw"
          className="object-cover object-center"
        />
        {/* Overlay bleu nuit profond */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(105deg, rgba(26,26,46,0.97) 0%, rgba(26,26,46,0.80) 60%, rgba(184,147,42,0.30) 100%)",
          }}
        />
        {/* Motif géométrique */}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 32px)",
          }}
        />
        {/* Contenu */}
        <div className="relative z-10 mx-auto max-w-container px-6 sm:px-10 h-full flex items-center justify-between gap-6 py-6">
          <div className="flex items-center gap-4">
            <div
              className="w-10 h-10 rounded-lg flex items-center justify-center text-lg flex-shrink-0"
              style={{ background: "rgba(184,147,42,0.18)", backdropFilter: "blur(6px)" }}
            >
              ⚙️
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-2 mb-0.5">
                <div className="h-px w-5" style={{ background: "#D4A017" }} />
                <span className="text-xxs text-gold-400 tracking-[0.2em] uppercase font-ui">
                  Espace Administration
                </span>
              </div>
              <h1 className="font-heading text-2xl font-bold text-white">
                Circuits de validation
              </h1>
            </div>
          </div>
          {/* Breadcrumb */}
          <nav className="hidden sm:flex items-center gap-2 text-xs font-ui text-neutral-400">
            <Link href="/" className="hover:text-gold-300 transition-colors">Accueil</Link>
            <span className="text-neutral-600">›</span>
            <span className="text-gold-300">Administration</span>
          </nav>
        </div>
        {/* Bande kente bas */}
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* ── Contenu ── */}
      <main className="flex-1 mx-auto w-full max-w-container px-6 sm:px-10 py-8">
        {children}
      </main>
    </div>
  );
}
