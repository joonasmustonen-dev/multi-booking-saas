import {
    useCallback,
    useEffect,
    useId,
    useRef,
    useState,
    type KeyboardEvent
} from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown, Search, X } from "lucide-react";
import { filterSelectOptions, nextEnabledOption } from "./selectOptions";
export interface SelectOption {
    value: string;
    label: string;
    description?: string;
    disabled?: boolean;
    tone?: string;
}
interface Props {
    value: string;
    options: SelectOption[];
    onChange: (value: string) => void;
    placeholder?: string;
    ariaLabel: string;
    searchable?: boolean;
    disabled?: boolean;
    required?: boolean;
    className?: string;
    onSearchChange?: (query: string) => void;
    filterLocally?: boolean;
    loading?: boolean;
    selectedLabel?: string;
}
export default function SearchSelect({
    value,
    options,
    onChange,
    placeholder = "Select…",
    ariaLabel,
    searchable = true,
    disabled = false,
    required = false,
    className = "",
    onSearchChange,
    filterLocally = true,
    loading = false,
    selectedLabel
}: Props) {
    const id = useId();
    const anchor = useRef<HTMLDivElement>(null);
    const menu = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [highlight, setHighlight] = useState(0);
    const [position, setPosition] = useState({
        left: 0,
        top: 0,
        width: 240,
        maxHeight: 280
    });
    const selected = options.find(option => option.value === value);
    const filtered = filterLocally
        ? filterSelectOptions(options, query)
        : options;
    const place = useCallback(() => {
        const rect = anchor.current?.getBoundingClientRect();
        if (!rect) return;
        const width = Math.min(
            Math.max(rect.width, 220),
            window.innerWidth - 16
        );
        const below = window.innerHeight - rect.bottom - 16;
        const above = rect.top - 16;
        const maxHeight = Math.min(
            280,
            Math.max(80, below < 160 && above > below ? above : below)
        );
        const visibleHeight = Math.min(
            maxHeight,
            menu.current?.scrollHeight ??
                filtered.length * 48 + (searchable ? 35 : 12)
        );
        const top =
            below < 160 && above > below
                ? Math.max(8, rect.top - visibleHeight - 8)
                : rect.bottom + 8;
        setPosition({
            left: Math.max(
                8,
                Math.min(rect.left, window.innerWidth - width - 8)
            ),
            top,
            width,
            maxHeight
        });
    }, [filtered.length, searchable]);
    function show() {
        if (disabled) return;
        place();
        setOpen(true);
        setQuery("");
        onSearchChange?.("");
        setHighlight(
            Math.max(
                0,
                options.findIndex(option => option.value === value)
            )
        );
    }
    function choose(option: SelectOption) {
        if (disabled || option.disabled) return;
        onChange(option.value);
        setOpen(false);
        setQuery("");
    }
    useEffect(() => {
        if (!open) return;
        place();
        const closeOutside = (event: PointerEvent) => {
            const target = event.target as Node;
            if (
                !anchor.current?.contains(target) &&
                !menu.current?.contains(target)
            )
                setOpen(false);
        };
        document.addEventListener("pointerdown", closeOutside);
        window.addEventListener("resize", place);
        window.addEventListener("scroll", place, true);
        return () => {
            document.removeEventListener("pointerdown", closeOutside);
            window.removeEventListener("resize", place);
            window.removeEventListener("scroll", place, true);
        };
    }, [open, query, options.length, place]);
    function keyboard(event: KeyboardEvent) {
        if (event.key === "Escape") {
            setOpen(false);
            setQuery("");
            event.preventDefault();
            return;
        }
        if (event.key === "Tab") {
            setOpen(false);
            setQuery("");
            return;
        }
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            if (!open) {
                show();
                return;
            }
            const direction = event.key === "ArrowDown" ? 1 : -1;
            const next = nextEnabledOption(filtered, highlight, direction);
            setHighlight(next);
            document
                .getElementById(`${id}-${next}`)
                ?.scrollIntoView({ block: "nearest" });
        }
        if (event.key === "Enter") {
            event.preventDefault();
            if (!open) show();
            else if (!loading && filtered[highlight])
                choose(filtered[highlight]);
        }
    }
    const common = {
        role: "combobox",
        "aria-label": ariaLabel,
        "aria-expanded": open,
        "aria-controls": `${id}-menu`,
        "aria-activedescendant":
            open && filtered[highlight] ? `${id}-${highlight}` : undefined,
        disabled,
        onKeyDown: keyboard
    };
    return (
        <div
            ref={anchor}
            className={`search-select ${open ? "is-open" : ""} ${className}`}
        >
            {searchable ? (
                <>
                    <Search size={16} className="search-select-icon" />
                    <input
                        {...common}
                        aria-autocomplete="list"
                        required={required}
                        value={
                            open
                                ? query
                                : (selected?.label ?? selectedLabel ?? "")
                        }
                        placeholder={
                            selected?.label ?? selectedLabel ?? placeholder
                        }
                        onFocus={show}
                        onClick={() => {
                            if (!open) show();
                        }}
                        onBlur={() => {
                            setOpen(false);
                            setQuery("");
                        }}
                        onChange={e => {
                            setQuery(e.target.value);
                            onSearchChange?.(e.target.value);
                            setHighlight(0);
                        }}
                    />
                </>
            ) : (
                <button
                    {...common}
                    type="button"
                    onClick={() => (open ? setOpen(false) : show())}
                >
                    <span>
                        {selected?.tone && (
                            <i
                                className={`status-dot status-${selected.tone}`}
                            />
                        )}
                        {selected?.label ?? selectedLabel ?? placeholder}
                    </span>
                </button>
            )}
            <ChevronDown size={16} className="search-select-chevron" />
            {open &&
                typeof document !== "undefined" &&
                createPortal(
                    <div
                        ref={menu}
                        id={`${id}-menu`}
                        role="listbox"
                        aria-label={ariaLabel}
                        className="select-popover"
                        style={position}
                    >
                        {searchable && (
                            <div className="select-popover-hint">
                                {loading
                                    ? "Searching…"
                                    : "Type to search · Enter to choose"}
                            </div>
                        )}
                        {filtered.length === 0 ? (
                            <p className="select-empty">No matching options</p>
                        ) : (
                            filtered.map((option, index) => (
                                <button
                                    type="button"
                                    tabIndex={-1}
                                    id={`${id}-${index}`}
                                    role="option"
                                    aria-selected={value === option.value}
                                    disabled={
                                        disabled || loading || option.disabled
                                    }
                                    key={option.value}
                                    className={`select-option ${option.tone ? "status-" + option.tone : ""} ${highlight === index ? "highlighted" : ""}`}
                                    onMouseDown={e => e.preventDefault()}
                                    onMouseEnter={() => setHighlight(index)}
                                    onClick={() => choose(option)}
                                >
                                    <span>
                                        <strong>
                                            {option.tone && (
                                                <i
                                                    className={`status-dot status-${option.tone}`}
                                                />
                                            )}
                                            {option.label}
                                        </strong>
                                        {option.description && (
                                            <small>{option.description}</small>
                                        )}
                                    </span>
                                    {value === option.value && (
                                        <Check size={16} />
                                    )}
                                </button>
                            ))
                        )}
                    </div>,
                    document.body
                )}
        </div>
    );
}
export function SearchMultiSelect({
    values,
    options,
    onChange,
    ariaLabel,
    disabled = false
}: {
    values: string[];
    options: SelectOption[];
    onChange: (values: string[]) => void;
    ariaLabel: string;
    disabled?: boolean;
}) {
    return (
        <div className="multi-select">
            <div className="selected-tags">
                {values.map(value => (
                    <span className="selected-tag" key={value}>
                        {options.find(option => option.value === value)
                            ?.label ?? "Unavailable location"}
                        <button
                            type="button"
                            disabled={disabled}
                            aria-label={`Remove ${options.find(option => option.value === value)?.label ?? "location"}`}
                            onClick={() =>
                                onChange(values.filter(item => item !== value))
                            }
                        >
                            <X size={12} />
                        </button>
                    </span>
                ))}
            </div>
            <SearchSelect
                value=""
                options={options.filter(
                    option => !values.includes(option.value)
                )}
                onChange={value => onChange([...values, value])}
                ariaLabel={ariaLabel}
                disabled={disabled}
                placeholder="Type to add a location…"
            />
        </div>
    );
}
