"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";

export default function Modal({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const overlayRef = useRef<HTMLDivElement>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    // On ne bloque plus le scroll du body pour permettre de scroller si le modal est grand
    return () => {};
  }, []);

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === overlayRef.current) {
      router.back();
    }
  };

  return (
    <div className={`col-start-1 row-start-1 relative h-full w-full z-[100] flex items-start justify-center p-4 sm:p-6 md:p-8 transition-opacity duration-200 ${mounted ? "opacity-100" : "opacity-0"}`}>
      {/* Overlay opaque simple */}
      <div 
        ref={overlayRef}
        className="absolute inset-0 bg-black/60 cursor-pointer" 
        onClick={handleOverlayClick}
      />
      
      {/* Conteneur Modal blanc simple */}
      <div
        className="relative z-10 bg-white w-full max-w-5xl rounded-2xl shadow-xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200 my-auto"
      >
        {/* Filigrane africain — visible dans les espaces entre les cards */}
        <div
          className="absolute inset-0 opacity-[0.05] pointer-events-none"
          style={{
            backgroundImage: "url('/Image_africaine6_resize.png')",
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        />
        <button
          onClick={() => router.back()}
          className="absolute top-6 right-6 z-50 flex items-center justify-center w-8 h-8 rounded-full bg-neutral-100 hover:bg-neutral-200 text-neutral-500 hover:text-neutral-900 shadow-sm transition-colors"
        >
          <X size={18} strokeWidth={2} />
        </button>
        {/* Retrait de flex-1 et overflow-y-auto pour que la div prenne sa taille réelle */}
        <div className="relative z-10 w-full p-2 sm:p-4">
          {children}
        </div>
      </div>
    </div>
  );
}
