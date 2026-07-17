import React from "react";

export default function PageTitle({
  badge,
  title,
  rightElement,
}: {
  badge: string;
  title: string;
  rightElement?: React.ReactNode;
}) {
  return (
    <section className="bg-white/70 backdrop-blur-sm border-b border-neutral-200 px-8 sm:px-16 py-8">
      <div className="mx-auto max-w-container flex items-center sm:items-end justify-between gap-4">
        <div>
          <p className="text-xxs text-secondary-500 tracking-[0.2em] uppercase font-ui mb-1">
            {badge}
          </p>
          <h2 className="font-heading text-3xl font-bold text-primary-500">
            {title}
          </h2>
        </div>
        {rightElement ? (
          <div className="flex items-center gap-4">
            {rightElement}
            {/* Séparateur or */}
            <div
              className="hidden sm:block h-12 w-1 rounded-full"
              style={{ background: "linear-gradient(180deg, #D4A017, transparent)" }}
            />
          </div>
        ) : (
          <div
            className="hidden sm:block h-12 w-1 rounded-full"
            style={{ background: "linear-gradient(180deg, #D4A017, transparent)" }}
          />
        )}
      </div>
    </section>
  );
}
