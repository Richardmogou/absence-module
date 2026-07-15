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
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "auto";
    };
  }, []);

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === overlayRef.current) {
      router.back();
    }
  };

  return (
    <div className={`fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6 md:p-12 transition-opacity duration-200 ${mounted ? "opacity-100" : "opacity-0"}`}>
      {/* Overlay opaque simple */}
      <div 
        ref={overlayRef}
        className="absolute inset-0 bg-black/60 cursor-pointer" 
        onClick={handleOverlayClick}
      />
      
      {/* Conteneur Modal blanc simple */}
      <div
        className="relative z-10 bg-white w-full max-w-5xl max-h-[90vh] rounded-2xl shadow-xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
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
        <div className="relative z-10 flex-1 overflow-y-auto w-full p-2 sm:p-4">
          {children}
        </div>
      </div>
    </div>
  );
}
