import { useQueryClient } from '@tanstack/react-query';
import { Box, ChevronDown, Loader2, LogOut, MapPin, Menu, X } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useCurrentOwner } from '../queries';
import type { UserAccountResponse } from '../types';

const NAV_ITEMS = [
  { to: '/locations', label: 'Lokalizacje', icon: MapPin },
  { to: '/service-objects', label: 'Urządzenia', icon: Box },
];

export function AppLayout({ user, children }: { user: UserAccountResponse; children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  // Na telefonie menu boczne zamyka się po przejściu do innej strony.
  useEffect(() => setMenuOpen(false), [location.pathname]);

  return (
    <div className={`shell${menuOpen ? ' shell-menu-open' : ''}`}>
      <aside className="sidebar" aria-label="Menu główne">
        <div className="sidebar-brand">
          <span className="brand-mark" aria-hidden>S</span>
          <span className="brand-name">Serwio</span>
          <button type="button" className="btn-icon sidebar-close" onClick={() => setMenuOpen(false)} aria-label="Zamknij menu">
            <X size={16} />
          </button>
        </div>
        <nav className="sidebar-nav">
          <span className="sidebar-section">Moje obiekty</span>
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon size={16} aria-hidden />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="sidebar-scrim" onClick={() => setMenuOpen(false)} aria-hidden />

      <div className="main">
        <header className="topbar">
          <button type="button" className="btn-icon topbar-menu" onClick={() => setMenuOpen(true)} aria-label="Otwórz menu">
            <Menu size={18} />
          </button>
          <div className="topbar-spacer" />
          <AccountMenu user={user} />
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}

function AccountMenu({ user }: { user: UserAccountResponse }) {
  const owner = useCurrentOwner();
  const [open, setOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  async function logout() {
    setLoggingOut(true);
    try {
      await api.logout();
    } finally {
      queryClient.clear();
      navigate('/sign-in', { replace: true, state: { reason: 'signed-out' } });
    }
  }

  const displayName = owner.data?.name ?? user.email;

  return (
    <div className="account" ref={ref}>
      <button
        type="button"
        className="account-trigger"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <span className="avatar" aria-hidden>{displayName.charAt(0).toUpperCase()}</span>
        <span className="account-text">
          <span className="account-name">{displayName}</span>
          {owner.data && <span className="account-email">{user.email}</span>}
        </span>
        <ChevronDown size={14} aria-hidden className="account-chevron" />
      </button>
      {open && (
        <div className="account-menu" role="menu">
          <div className="account-menu-header">
            <span className="account-name">{displayName}</span>
            <span className="account-email">{user.email}</span>
            <span className="badge">Właściciel obiektów</span>
          </div>
          <button type="button" role="menuitem" className="menu-item" onClick={logout} disabled={loggingOut}>
            {loggingOut ? <Loader2 size={16} className="spin" aria-hidden /> : <LogOut size={16} aria-hidden />}
            {loggingOut ? 'Wylogowywanie…' : 'Wyloguj się'}
          </button>
        </div>
      )}
    </div>
  );
}
