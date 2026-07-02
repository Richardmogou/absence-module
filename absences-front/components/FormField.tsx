"use client";

import { type FieldError } from "react-hook-form";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface FormFieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: FieldError;
}

export function FormField({ label, error, className, ...props }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={props.id}>{label}</Label>
      <Input className={cn(error && "border-secondary-500 focus:ring-secondary-500", className)} {...props} />
      {error && <p className="text-xs text-secondary-500">{error.message}</p>}
    </div>
  );
}
