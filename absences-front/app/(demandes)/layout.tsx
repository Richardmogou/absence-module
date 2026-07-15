import Image from "next/image";

import { PageHeader } from "@/components/PageHeader";

export default function DemandesLayout({ children, modal }: { children: React.ReactNode, modal: React.ReactNode }) {
  return (
    <div className="flex flex-col flex-1 relative">
        {/* ══════════════════════════════════════
          SECTION TITRE DYNAMIQUE
          ══════════════════════════════════════ */}
      <PageHeader />
      {/* Bande kente fine en haut */}
      <div
        className="h-1 w-full"
        // style={{
        //   background:
        //     "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 10px,#B8932A 10px,#B8932A 20px,#2C2C2C 20px,#2C2C2C 30px,#F5F5F5 30px,#F5F5F5 40px)",
        // }}
      />
      {/* Image africaine en fond très atténuée */}
      <div
        className="absolute inset-0 opacity-[0.04] pointer-events-none"
        style={{
          backgroundImage: "url('/Image_Afrique3_resize.png')",
          
          backgroundSize: "cover",
          backgroundPosition: "center",
          top: "4px",
        }}
      />
      <main className="relative z-10 mx-auto w-full max-w-container px-6 lg:px-8 py-4">
        {children}
        {modal}
      </main>
      
            {/* ══════════════════════════════════════
                      FOOTER DÉCORATIF
                      ══════════════════════════════════════ */}
                  <footer className="px-6 lg:px-8 py-6 bg-white/50">
                    <div className="mx-auto max-w-container flex items-center gap-4">
                      <div className="flex-1 h-px"
                        style={{ background: "linear-gradient(90deg, transparent, #B8932A)" }} />
                      <Image
                        src="/icon_afb.png"
                        alt=""
                        aria-hidden="true"
                        width={20}
                        height={20}
                        className="opacity-40"
                      />
                      <div className="flex-1 h-px"
                        style={{ background: "linear-gradient(90deg, #B8932A, transparent)" }} />
                    </div>
                  </footer>
    </div>
  );
}
