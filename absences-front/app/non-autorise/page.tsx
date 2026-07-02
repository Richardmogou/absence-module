import Link from "next/link";

export default function NonAutorisePage() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-6 px-6 py-20 text-center">
      <div className="flex h-20 w-20 items-center justify-center rounded-full bg-secondary-100 text-4xl">
        🔒
      </div>
      <div className="flex flex-col gap-2">
        <h1 className="font-heading text-3xl font-bold text-primary-500">
          Accès refusé
        </h1>
        <p className="text-sm text-neutral-500 max-w-sm">
          Vous n&apos;avez pas les droits nécessaires pour accéder à cette page.
          Contactez votre administrateur si vous pensez qu&apos;il s&apos;agit d&apos;une erreur.
        </p>
      </div>
      <Link
        href="/mon-espace"
        className="inline-flex h-10 items-center rounded-md bg-primary-500 px-5 text-sm font-medium text-white transition-colors hover:bg-primary-600"
      >
        Retour à mon espace
      </Link>
    </div>
  );
}
