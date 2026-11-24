import {
  faChevronLeft,
  faChevronRight,
} from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useState, type ReactNode } from "react";

type SidePanelProps = {
  title: string;
  children: ReactNode;
  defaultOpen?: boolean;
};

function SidePanel({ title, children, defaultOpen = true }: SidePanelProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <aside
      className={`absolute top-1/2 left-4 z-10 flex max-h-[calc(100%-2rem)] w-[min(22.5rem,calc(100vw-3.5rem))] -translate-y-1/2 flex-col rounded-2xl bg-white shadow-lg transition-transform duration-250 ${
        open ? "translate-x-0" : "translate-x-[calc(-100%-1rem)]"
      }`}
    >
      <div
        className="min-h-0 overflow-y-auto rounded-[inherit] p-5"
        inert={!open}
      >
        <h1 className="mb-4 text-xl font-bold">{title}</h1>
        {children}
      </div>

      <button
        type="button"
        className="absolute top-1/2 left-full grid h-12 w-6 -translate-y-1/2 cursor-pointer place-items-center rounded-r-lg bg-white text-muted shadow-[2px_0_4px_rgb(0_0_0/0.15)] hover:text-primary"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={open ? "Schowaj panel" : "Pokaż panel"}
      >
        <FontAwesomeIcon icon={open ? faChevronLeft : faChevronRight} />
      </button>
    </aside>
  );
}

export default SidePanel;
