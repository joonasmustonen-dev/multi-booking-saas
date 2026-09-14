import { useEffect, useRef, type ReactNode } from "react";
export default function LocationEditorDialog({ children, onClose }: { children: ReactNode; onClose: () => void }) {
    const ref = useRef<HTMLDialogElement>(null);
    useEffect(() => { const dialog = ref.current; if (dialog && !dialog.open) dialog.showModal(); return () => { if (dialog?.open) dialog.close(); }; }, []);
    return <dialog ref={ref} className="location-editor-dialog" aria-label="Location editor" onCancel={event => { event.preventDefault(); if (!ref.current?.querySelector('fieldset:disabled')) onClose(); }}>{children}</dialog>;
}
