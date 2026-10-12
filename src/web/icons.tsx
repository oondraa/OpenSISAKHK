// Line icons on a 24×24 grid, stroked with currentColor (styled by .icon).
import type { ReactNode } from "react";

function Icon({ children, size }: { children: ReactNode; size?: number }) {
  return (
    <svg className="icon" viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
      {children}
    </svg>
  );
}

export const CalendarIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <rect x="3.5" y="5" width="17" height="15.5" rx="3.5" />
    <path d="M3.5 10h17M8 3v4M16 3v4" />
  </Icon>
);

export const DumbbellIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <rect x="4.5" y="7" width="3" height="10" rx="1.2" />
    <rect x="16.5" y="7" width="3" height="10" rx="1.2" />
    <path d="M7.5 12h9M2.5 10v4M21.5 10v4" />
  </Icon>
);

export const GradesIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M5 20v-7M12 20V5M19 20v-10" />
  </Icon>
);

export const GearIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
    <circle cx="12" cy="12" r="3" />
  </Icon>
);

export const UtensilsIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M4 3v6a3 3 0 0 0 3 3v9M10 3v6a3 3 0 0 1-3 3M7 3v5M20 15V3a5 5 0 0 0-4 5v5a2 2 0 0 0 2 2h2zm0 0v6" />
  </Icon>
);

export const SunIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
  </Icon>
);

export const CalendarOffIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <rect x="3.5" y="5" width="17" height="15.5" rx="3.5" />
    <path d="M3.5 10h17M8 3v4M16 3v4M10 13.5l4 4M14 13.5l-4 4" />
  </Icon>
);

export const BookIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M12 6.5C10.5 5 8 4.5 4 4.5v14c4 0 6.5.5 8 2 1.5-1.5 4-2 8-2v-14c-4 0-6.5.5-8 2zM12 6.5v14" />
  </Icon>
);

export const AlertIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4.5M12 17h.01" />
  </Icon>
);
