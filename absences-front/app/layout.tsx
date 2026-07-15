import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import AppHeader from "@/components/AppHeader";
import AppFooter from "@/components/AppFooter";
import SessionProviderWrapper from "@/components/SessionProviderWrapper";
import { auth } from "@/auth";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Absences — AFB",
  description: "Gestion des absences",
  icons: { icon: "/favicon.ico" },
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await auth();

  return (
    <html
      lang="fr"
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased font-sans`}
    >
      <body
        suppressHydrationWarning
        className="min-h-full flex flex-col"
        style={{
          backgroundImage: "url('/background/background_website.png')",
          backgroundRepeat: "repeat",
          backgroundSize: "auto",
        }}
      >
        <SessionProviderWrapper session={session}>
          <AppHeader />
          <div className="flex flex-col flex-1">{children}</div>
          <AppFooter />
        </SessionProviderWrapper>
      </body>
    </html>
  );
}
