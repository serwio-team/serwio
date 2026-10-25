import { useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Building2,
  ChevronDown,
  LayoutDashboard,
  Loader2,
  LogOut,
  MapPin,
  Menu,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useCurrentCompany, useCurrentOwner, useCurrentTechnician } from '../queries';
import type { UserAccountResponse } from '../types';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

const NAV_BY_ROLE: Record<string, { section: string; items: NavItem[] }> = {
  ASSET_OWNER: {
    section: 'Moje obiekty',
    items: [
      { to: '/locations', label: 'Lokalizacje', icon: MapPin },
      { to: '/service-objects', label: 'Urządzenia', icon: Box },
    ],
  },
  SERVICE_COMPANY: {
    section: 'Firma serwisowa',
    items: [
      { to: '/company', label: 'Pulpit', icon: LayoutDashboard },
      { to: '/company/branches', label: 'Oddziały', icon: Building2 },
      { to: '/company/technicians', label: 'Serwisanci', icon: Users },
    ],
  },
  TECHNICIAN: {
    section: 'Serwisant',
    items: [{ to: '/technician', label: 'Mój panel', icon: LayoutDashboard }],
  },
};

const ROLE_LABELS: Record<string, string> = {
  ASSET_OWNER: 'Właściciel obiektów',
  SERVICE_COMPANY: 'Firma serwisowa',
  TECHNICIAN: 'Serwisant',
};

export function AppLayout({ user, children }: { user: UserAccountResponse; children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const nav = NAV_BY_ROLE[user.role];

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
          <span className="sidebar-section">{nav?.section}</span>
          {nav?.items.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/company'}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
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
  const owner = useCurrentOwner(user.role === 'ASSET_OWNER');
  const company = useCurrentCompany(user.role === 'SERVICE_COMPANY');
  const technician = useCurrentTechnician(user.role === 'TECHNICIAN');
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

  const profileName = owner.data?.name ?? company.data?.name ?? technician.data?.displayName;
  const displayName = profileName ?? user.email;

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
          {profileName && <span className="account-email">{user.email}</span>}
        </span>
        <ChevronDown size={14} aria-hidden className="account-chevron" />
      </button>
      {open && (
        <div className="account-menu" role="menu">
          <div className="account-menu-header">
            <span className="account-name">{displayName}</span>
            <span className="account-email">{user.email}</span>
            <span className="badge">{ROLE_LABELS[user.role] ?? user.role}</span>
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
