import { useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Plus, Save, Trash2 } from 'lucide-react';
import type { AssignmentKind } from '../assignments/assignmentTypes';
import type { AvailabilityRule } from './availabilityTypes';
import { replaceAvailabilityRules } from './availabilityApi';
import { invalidateBookingData } from '../assignments/invalidateBookingData';
import { weekDays, toggleRecurringDay, recurringWeekdaysPreset, validateRecurringDraft, type RecurringDraft } from './recurringDraft';
export default function RecurringWeekEditor({ kind, ownerId, initialRules }: { kind: AssignmentKind; ownerId: string; initialRules: AvailabilityRule[] }) {
    const client = useQueryClient(); const [rules, setRules] = useState<RecurringDraft[]>(initialRules.map(rule => ({ dayOfWeek: rule.dayOfWeek, startTime: rule.startTime.slice(0, 5), endTime: rule.endTime.slice(0, 5), active: rule.active })));
    const [pending, setPending] = useState(false); const [error, setError] = useState(''); const [saved, setSaved] = useState(false);
    function change(next: RecurringDraft[]) { setRules(next); setSaved(false); setError(''); }
    function update(index: number, changes: Partial<RecurringDraft>) { change(rules.map((rule, i) => i === index ? { ...rule, ...changes } : rule)); }
    function copyMonday() { const monday = rules.filter(rule => rule.dayOfWeek === 'MONDAY'); change([...rules.filter(rule => !weekDays.slice(1, 5).includes(rule.dayOfWeek)), ...weekDays.slice(1, 5).flatMap(dayOfWeek => monday.map(rule => ({ ...rule, dayOfWeek })))]); }
    async function save(event: FormEvent) {
        event.preventDefault(); if (pending) return; const message = validateRecurringDraft(rules); if (message) { setError(message); return; }
        setPending(true); setError(''); try { await replaceAvailabilityRules(kind, ownerId, rules); await invalidateBookingData(client); setSaved(true); }
        catch (e) { setError(e instanceof Error ? e.message : 'Could not save recurring hours.'); } finally { setPending(false); }
    }
    return <form className="recurring-week" onSubmit={save}><p className="field-note">Click a day to open or close it, then set its times. Changes apply when you save.</p>
        <fieldset className="editor-fields" disabled={pending}><div className="recurring-day-strip">{weekDays.map(day => { const active = rules.some(rule => rule.dayOfWeek === day && rule.active); return <button type="button" key={day} className="day-dot-button" aria-label={`${day.charAt(0) + day.slice(1).toLowerCase()} recurring hours`} aria-pressed={active} onClick={() => change(toggleRecurringDay(rules, day, !active))}><span>{active ? <Check size={17} /> : day.slice(0, 1)}</span>{day.slice(0, 3).toLowerCase().replace(/^./, c => c.toUpperCase())}</button>; })}</div>
        <div className="recurring-presets"><button type="button" className="button button-secondary" onClick={() => change(recurringWeekdaysPreset())}>Weekdays 09–17</button><button type="button" className="button button-secondary" onClick={copyMonday}><Copy size={14} />Copy Monday to weekdays</button><button type="button" className="text-action" onClick={() => change(rules.map(rule => ({ ...rule, active: false })))}>Close all days</button></div>
        <div className="recurring-day-rows">{weekDays.filter(day => rules.some(rule => rule.dayOfWeek === day && rule.active)).map(day => <div className="recurring-day-row" key={day}><strong>{day.charAt(0) + day.slice(1).toLowerCase()}</strong><div className="recurring-shifts">{rules.map((rule, index) => ({ rule, index })).filter(entry => entry.rule.dayOfWeek === day).map(({ rule, index }) => <div className="recurring-time-row" key={index}><label className="form-field">From<input className="input" type="time" required value={rule.startTime} onChange={e => update(index, { startTime: e.target.value })} /></label><label className="form-field">Until<input className="input" type="time" required value={rule.endTime} onChange={e => update(index, { endTime: e.target.value })} /></label><label className="check-row"><input type="checkbox" checked={rule.active} onChange={e => update(index, { active: e.target.checked })} />Active</label><button type="button" className="button button-secondary" aria-label={`Remove ${day.toLowerCase()} time period`} onClick={() => change(rules.filter((_, i) => i !== index))}><Trash2 size={15} /></button></div>)}<button type="button" className="text-action" disabled={rules.length >= 28} onClick={() => change([...rules, { dayOfWeek: day, startTime: '18:00', endTime: '20:00', active: true }])}><Plus size={13} />Add split hours</button></div></div>)}</div></fieldset>
        {error && <p className="form-error" role="alert">{error}</p>}{saved && <p className="field-note" role="status">Recurring hours saved.</p>}<div className="form-actions"><button className="button button-primary" disabled={pending}><Save size={15} />{pending ? 'Saving…' : 'Save recurring hours'}</button></div>
    </form>;
}
