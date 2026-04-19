import { useState, useRef, useEffect } from "react";
import { Search, MapPin, Check, ChevronDown } from "lucide-react";

interface TerminalSelectProps {
  label: string;
  iconColor: string;
  options: { id: string; name: string }[];
  value: string;
  onChange: (val: string) => void;
  placeholder?: string;
  isLoading?: boolean;
}

export function TerminalSelect({
  label,
  iconColor,
  options,
  value,
  onChange,
  placeholder = "Select terminal...",
  isLoading
}: TerminalSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filtered = options?.content?.filter(opt => opt.name.toLowerCase().includes(query.toLowerCase()));
  const selected = options?.content?.find(o => o.id === value);

  return (
    <div className="flex flex-col gap-2" ref={ref}>
      <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-2">
        <MapPin className="h-3.5 w-3.5" style={{ color: iconColor }} />
        {label}
      </label>
      
      <div className="relative">
        <button
          type="button"
          onClick={() => setOpen(!open)}
          className="w-full h-11 px-4 text-left rounded-lg border border-border bg-white flex items-center justify-between focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
        >
          <span className={selected ? "text-foreground" : "text-muted-foreground"}>
            {isLoading ? "Loading terminals..." : selected ? selected.name : placeholder}
          </span>
          <ChevronDown className="h-4 w-4 text-muted-foreground opacity-50" />
        </button>

        {open && (
          <div className="absolute z-50 w-full mt-1 bg-white border border-border rounded-xl shadow-xl overflow-hidden animate-in fade-in slide-in-from-top-2">
            <div className="p-2 border-b border-border flex items-center gap-2 px-3">
              <Search className="h-4 w-4 text-muted-foreground" />
              <input 
                autoFocus
                className="w-full h-8 bg-transparent text-[13px] outline-none font-medium placeholder:text-muted-foreground"
                placeholder="Search backend..."
                value={query}
                onChange={e => setQuery(e.target.value)}
              />
            </div>
            <div className="max-h-[240px] overflow-y-auto p-1">
              {filtered.length === 0 ? (
                <div className="py-6 text-center text-[13px] text-muted-foreground">No terminals found.</div>
              ) : (
                filtered.map(opt => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => {
                      onChange(opt.id);
                      setOpen(false);
                      setQuery("");
                    }}
                    className={`w-full text-left px-3 py-2.5 rounded-lg text-[13px] font-semibold flex items-center justify-between ${value === opt.id ? 'bg-primary/10 text-primary' : 'hover:bg-surface-container text-foreground'}`}
                  >
                    <span className="flex items-center gap-2">
                       <MapPin className="h-3 w-3" style={{ color: value === opt.id ? iconColor : '#ccc' }} />
                       {opt.name}
                    </span>
                    {value === opt.id && <Check className="h-4 w-4" />}
                  </button>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
