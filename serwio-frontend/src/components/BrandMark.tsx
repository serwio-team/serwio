import { useId } from 'react';

/** Znak Serwio: litera S jako trasa od zlecenia do wizyty. Źródło: public/logo.svg. */
export function BrandMark({ size = 26 }: { size?: number }) {
  const gradient = `serwio-mark-${useId().replace(/:/g, '')}`;
  const fill = `url(#${gradient})`;
  return (
    <svg className="brand-mark" width={size} height={size} viewBox="0 0 96 96" aria-hidden focusable="false">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#6c78e0" />
          <stop offset="1" stopColor="#4c57c2" />
        </linearGradient>
      </defs>
      <rect width="96" height="96" rx="22" fill={fill} />
      <path
        d="M58 26H38a12 12 0 0 0 0 24h12a12 12 0 0 1 0 24H26"
        fill="none" stroke="#fff" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round"
      />
      <circle cx="67" cy="26" r="6" fill={fill} stroke="#fff" strokeWidth="5" />
    </svg>
  );
}
