export default function DemandesLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col flex-1 relative">
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
      <main className="relative z-10 mx-auto w-full max-w-container px-6 py-10">
        {children}
      </main>
    </div>
  );
}
