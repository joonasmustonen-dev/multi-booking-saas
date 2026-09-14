import {
    useEffect,
    useLayoutEffect,
    useRef,
    useState,
    type InputHTMLAttributes
} from "react";
import { createPortal } from "react-dom";
import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Clock3,
    X
} from "lucide-react";
import "./DateTimeInput.css";

export default function DateTimeInput(
    props: InputHTMLAttributes<HTMLInputElement>
) {
    const input = useRef<HTMLInputElement>(null);
    const panel = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0 });
    const value = String(props.value ?? "");
    const hasDate = props.type !== "time";
    const hasTime = props.type !== "date";
    const selectedDate = value.slice(0, 10);
    const clock =
        (props.type === "time" ? value : value.split("T")[1]) || "09:00";
    const [month, setMonth] = useState(() => new Date());

    useLayoutEffect(() => {
        if (!open || !panel.current || !input.current) return;
        const anchor = input.current.getBoundingClientRect();
        const height = panel.current.getBoundingClientRect().height;
        setPosition({
            left: Math.max(12, Math.min(anchor.left, window.innerWidth - 332)),
            top:
                anchor.bottom + height + 8 <= window.innerHeight - 12
                    ? anchor.bottom + 8
                    : Math.max(
                          12,
                          Math.min(
                              anchor.top - height - 8,
                              window.innerHeight - height - 12
                          )
                      )
        });
    }, [open]);

    useEffect(() => {
        if (!open) return;
        const close = (event: PointerEvent) => {
            if (
                !panel.current?.contains(event.target as Node) &&
                !input.current?.parentElement?.contains(event.target as Node)
            )
                setOpen(false);
        };
        const escape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setOpen(false);
                input.current?.focus();
            }
        };
        document.addEventListener("pointerdown", close);
        document.addEventListener("keydown", escape);
        return () => {
            document.removeEventListener("pointerdown", close);
            document.removeEventListener("keydown", escape);
        };
    }, [open]);

    function show() {
        if (!input.current || input.current.matches(":disabled")) return;
        const bounds = input.current.getBoundingClientRect();
        const height = hasDate ? (hasTime ? 450 : 365) : 235;
        setPosition({
            left: Math.max(12, Math.min(bounds.left, window.innerWidth - 332)),
            top:
                bounds.bottom + height < window.innerHeight
                    ? bounds.bottom + 8
                    : Math.max(12, bounds.top - height - 8)
        });
        const date =
            hasDate && selectedDate
                ? new Date(`${selectedDate}T12:00:00`)
                : new Date();
        setMonth(Number.isNaN(date.getTime()) ? new Date() : date);
        setOpen(true);
    }

    function choose(next: string, close = true) {
        const element = input.current;
        if (!element || element.matches(":disabled")) return;
        // Use the native value setter so React receives an ordinary input event.
        Object.getOwnPropertyDescriptor(
            HTMLInputElement.prototype,
            "value"
        )?.set?.call(element, next);
        element.dispatchEvent(new Event("input", { bubbles: true }));
        if (close) {
            setOpen(false);
            element.focus();
        }
    }

    const year = month.getFullYear();
    const monthIndex = month.getMonth();
    const firstOffset = (new Date(year, monthIndex, 1).getDay() + 6) % 7;
    const cells = Array.from(
        { length: 42 },
        (_, index) => new Date(year, monthIndex, index - firstOffset + 1)
    );
    const localDate = (date: Date) =>
        `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

    return (
        <span className="date-time-control">
            <input
                {...props}
                ref={input}
                onClick={event => {
                    props.onClick?.(event);
                    show();
                }}
            />
            <button
                type="button"
                className="date-time-trigger"
                aria-label={hasDate ? "Choose date" : "Choose time"}
                aria-expanded={open}
                disabled={props.disabled}
                onClick={event => {
                    event.preventDefault();
                    if (open) setOpen(false);
                    else show();
                }}
            >
                {hasDate ? <CalendarDays size={17} /> : <Clock3 size={16} />}
            </button>
            {open &&
                createPortal(
                    <div
                        className="date-time-popover"
                        ref={panel}
                        style={position}
                        role="dialog"
                        aria-label={hasDate ? "Choose a date" : "Choose a time"}
                    >
                        <div className="date-picker-heading">
                            <strong>
                                {hasDate
                                    ? month.toLocaleDateString(undefined, {
                                          month: "long",
                                          year: "numeric"
                                      })
                                    : "Choose a time"}
                            </strong>
                            <div>
                                {hasDate && (
                                    <>
                                        <button
                                            type="button"
                                            aria-label="Previous month"
                                            onClick={() =>
                                                setMonth(
                                                    new Date(
                                                        year,
                                                        monthIndex - 1,
                                                        1
                                                    )
                                                )
                                            }
                                        >
                                            <ChevronLeft size={16} />
                                        </button>
                                        <button
                                            type="button"
                                            aria-label="Next month"
                                            onClick={() =>
                                                setMonth(
                                                    new Date(
                                                        year,
                                                        monthIndex + 1,
                                                        1
                                                    )
                                                )
                                            }
                                        >
                                            <ChevronRight size={16} />
                                        </button>
                                    </>
                                )}
                                <button
                                    type="button"
                                    aria-label="Close picker"
                                    onClick={() => setOpen(false)}
                                >
                                    <X size={16} />
                                </button>
                            </div>
                        </div>
                        {hasDate && (
                            <>
                                <div className="date-picker-grid date-picker-weekdays">
                                    {[
                                        "Mo",
                                        "Tu",
                                        "We",
                                        "Th",
                                        "Fr",
                                        "Sa",
                                        "Su"
                                    ].map(day => (
                                        <span key={day}>{day}</span>
                                    ))}
                                </div>
                                <div className="date-picker-grid">
                                    {cells.map(date => {
                                        const iso = localDate(date);
                                        const disabled =
                                            (props.min &&
                                                iso <
                                                    String(props.min).slice(
                                                        0,
                                                        10
                                                    )) ||
                                            (props.max &&
                                                iso >
                                                    String(props.max).slice(
                                                        0,
                                                        10
                                                    ));
                                        return (
                                            <button
                                                type="button"
                                                key={iso}
                                                aria-label={iso}
                                                aria-pressed={
                                                    selectedDate === iso
                                                }
                                                className={`${date.getMonth() !== monthIndex ? "outside-month" : ""} ${iso === localDate(new Date()) ? "is-today" : ""}`}
                                                disabled={!!disabled}
                                                onClick={() =>
                                                    choose(
                                                        hasTime
                                                            ? `${iso}T${clock}`
                                                            : iso,
                                                        !hasTime
                                                    )
                                                }
                                            >
                                                {date.getDate()}
                                            </button>
                                        );
                                    })}
                                </div>
                            </>
                        )}
                        {hasTime && (
                            <div className="time-picker-section">
                                <strong>Hour · {clock}</strong>
                                <div className="time-picker-hours">
                                    {Array.from({ length: 24 }, (_, hour) =>
                                        String(hour).padStart(2, "0")
                                    ).map(hour => (
                                        <button
                                            type="button"
                                            key={hour}
                                            aria-pressed={
                                                clock.slice(0, 2) === hour
                                            }
                                            onClick={() =>
                                                choose(
                                                    `${hasDate ? `${selectedDate || localDate(new Date())}T` : ""}${hour}:${clock.slice(3, 5)}`,
                                                    false
                                                )
                                            }
                                        >
                                            {hour}
                                        </button>
                                    ))}
                                </div>
                                <div className="time-picker-minutes">
                                    {["00", "15", "30", "45"].map(minute => (
                                        <button
                                            type="button"
                                            key={minute}
                                            aria-label={`${minute} minutes`}
                                            aria-pressed={
                                                clock.slice(3, 5) === minute
                                            }
                                            onClick={() =>
                                                choose(
                                                    `${hasDate ? `${selectedDate || localDate(new Date())}T` : ""}${clock.slice(0, 2)}:${minute}`
                                                )
                                            }
                                        >
                                            :{minute}
                                        </button>
                                    ))}
                                </div>
                                <small>
                                    Type directly in the field for any other
                                    time.
                                </small>
                            </div>
                        )}
                        <div className="date-picker-footer">
                            {!props.required && (
                                <button
                                    type="button"
                                    onClick={() => choose("")}
                                >
                                    Clear
                                </button>
                            )}
                            {hasDate && (
                                <button
                                    type="button"
                                    onClick={() =>
                                        choose(
                                            hasTime
                                                ? `${localDate(new Date())}T${clock}`
                                                : localDate(new Date())
                                        )
                                    }
                                >
                                    Today
                                </button>
                            )}
                            <button
                                type="button"
                                onClick={() => setOpen(false)}
                            >
                                Done
                            </button>
                        </div>
                    </div>,
                    document.body
                )}
        </span>
    );
}
