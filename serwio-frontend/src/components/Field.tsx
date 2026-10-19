import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';

interface FieldShellProps {
  label: string;
  name: string;
  required?: boolean;
  errors?: string[];
  hint?: string;
  children: ReactNode;
}

function FieldShell({ label, name, required, errors, hint, children }: FieldShellProps) {
  const invalid = Boolean(errors?.length);
  return (
    <label className={`field${invalid ? ' field-invalid' : ''}`}>
      <span className="field-label">
        {label}
        {!required && <span className="field-optional"> (opcjonalnie)</span>}
      </span>
      {children}
      {invalid ? (
        <span id={`${name}-error`} className="field-error">{errors!.join(' ')}</span>
      ) : hint ? (
        <span className="field-hint">{hint}</span>
      ) : null}
    </label>
  );
}

const describedBy = (name: string, errors?: string[]) => (errors?.length ? `${name}-error` : undefined);

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  errors?: string[];
  hint?: string;
}

export function Field({ label, name, value, onChange, errors, hint, required, ...rest }: FieldProps) {
  return (
    <FieldShell label={label} name={name} required={required} errors={errors} hint={hint}>
      <input
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        aria-invalid={errors?.length ? true : undefined}
        aria-describedby={describedBy(name, errors)}
        {...rest}
      />
    </FieldShell>
  );
}

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'onChange'> {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  errors?: string[];
  children: ReactNode;
}

export function SelectField({ label, name, value, onChange, errors, required, children, ...rest }: SelectFieldProps) {
  return (
    <FieldShell label={label} name={name} required={required} errors={errors}>
      <select
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        aria-invalid={errors?.length ? true : undefined}
        aria-describedby={describedBy(name, errors)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
}

interface TextareaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange'> {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  errors?: string[];
}

export function TextareaField({ label, name, value, onChange, errors, required, ...rest }: TextareaFieldProps) {
  return (
    <FieldShell label={label} name={name} required={required} errors={errors}>
      <textarea
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        aria-invalid={errors?.length ? true : undefined}
        aria-describedby={describedBy(name, errors)}
        {...rest}
      />
    </FieldShell>
  );
}
