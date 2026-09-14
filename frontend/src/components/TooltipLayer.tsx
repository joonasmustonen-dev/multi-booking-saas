import { useEffect, useId, useState } from "react";
import { createPortal } from "react-dom";
interface Tip { text: string; left: number; top: number; below: boolean }
export default function TooltipLayer() {
    const id = useId(); const [tip, setTip] = useState<Tip | null>(null);
    useEffect(() => {
        let trigger: HTMLElement | null = null; let previous: string | null = null;
        function hide() { if (trigger) { if (previous) trigger.setAttribute("aria-describedby", previous); else trigger.removeAttribute("aria-describedby"); } trigger = null; setTip(null); }
        function show(event: Event) {
            const target = (event.target as Element)?.closest<HTMLElement>("[data-tooltip]");
            if (!target || target === trigger) return; hide();
            const text = target.dataset.tooltip; if (!text) return;
            trigger = target; previous = target.getAttribute("aria-describedby"); target.setAttribute("aria-describedby", [previous, id].filter(Boolean).join(" "));
            const rect = target.getBoundingClientRect(); const below = rect.top < 110;
            const halfWidth = Math.min(160, (window.innerWidth - 24) / 2);
            setTip({ text, left: Math.max(halfWidth + 12, Math.min(rect.left + rect.width / 2, window.innerWidth - halfWidth - 12)), top: below ? rect.bottom + 10 : rect.top - 10, below });
        }
        function leave(event: Event) { const related = (event as MouseEvent).relatedTarget as Node | null; if (!trigger?.contains(related)) hide(); }
        function key(event: KeyboardEvent) { if (event.key === "Escape") hide(); }
        document.addEventListener("pointerover", show); document.addEventListener("focusin", show); document.addEventListener("pointerout", leave); document.addEventListener("focusout", leave); document.addEventListener("keydown", key); window.addEventListener("scroll", hide, true);
        return () => { document.removeEventListener("pointerover", show); document.removeEventListener("focusin", show); document.removeEventListener("pointerout", leave); document.removeEventListener("focusout", leave); document.removeEventListener("keydown", key); window.removeEventListener("scroll", hide, true); if (trigger) { if (previous) trigger.setAttribute("aria-describedby", previous); else trigger.removeAttribute("aria-describedby"); } };
    }, [id]);
    return tip && typeof document !== "undefined" ? createPortal(<div role="tooltip" id={id} className={`styled-tooltip ${tip.below ? "below" : ""}`} style={{ left: tip.left, top: tip.top }}>{tip.text}</div>, document.body) : null;
}
