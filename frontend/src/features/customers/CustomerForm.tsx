import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import SearchSelect from '../../components/SearchSelect';
import { getStaff } from '../staff/staffApi';
import type { Customer, CreateCustomerRequest } from './customerTypes';

export default function CustomerForm({ customer, onSubmit, onCancel }: {
    customer?: Customer; onSubmit: (request: CreateCustomerRequest) => Promise<void>; onCancel: () => void;
}) {
    const [firstName, setFirstName] = useState(customer?.firstName ?? '');
    const [lastName, setLastName] = useState(customer?.lastName ?? '');
    const [email, setEmail] = useState(customer?.email ?? ''); const [phone, setPhone] = useState(customer?.phone ?? '');
    const [preferredStaffId, setPreferredStaffId] = useState(customer?.preferredStaffId ?? '');
    const [pending, setPending] = useState(false); const [error, setError] = useState('');
    const staff = useQuery({ queryKey: ['staff'], queryFn: getStaff });
    async function submit(event: FormEvent) {
        event.preventDefault(); if (pending) return; setPending(true); setError('');
        try { await onSubmit({ firstName: firstName.trim(), lastName: lastName.trim(), email: email.trim() || null,
            phone: phone.trim() || null, preferredStaffId: preferredStaffId || null }); }
        catch (e) { setError(e instanceof Error ? e.message : 'Could not save customer.'); }
        finally { setPending(false); }
    }
    const options = [{ value: '', label: 'No staff preference' }, ...(staff.data ?? []).filter(s => s.active || s.id === preferredStaffId)
        .map(s => ({ value: s.id, label: s.name, description: s.active ? s.email : 'Inactive · existing preference' }))];
    if (preferredStaffId && !options.some(s => s.value === preferredStaffId)) options.push({ value: preferredStaffId, label: 'Previous staff preference', description: 'Archived or unavailable' });
    return <form className="customer-form management-form" onSubmit={submit}><h3>{customer ? 'Edit customer' : 'Add customer'}</h3>
        <div className="form-grid"><label className="form-field">First name<input className="input" required maxLength={100} value={firstName} onChange={e => setFirstName(e.target.value)} /></label>
        <label className="form-field">Last name<input className="input" required maxLength={100} value={lastName} onChange={e => setLastName(e.target.value)} /></label>
        <label className="form-field">Email<input className="input" type="email" maxLength={320} value={email} onChange={e => setEmail(e.target.value)} /></label>
        <label className="form-field">Phone<input className="input" type="tel" maxLength={50} value={phone} onChange={e => setPhone(e.target.value)} /></label></div>
        <label className="form-field">Preferred staff<SearchSelect value={preferredStaffId} onChange={setPreferredStaffId} options={options} ariaLabel="Customer preferred staff" disabled={staff.isPending || !!staff.error} /></label>
        <p className="field-note">An informational preference; service eligibility and availability still determine booking options.</p>
        {staff.error && <p className="form-error">Staff preferences could not be loaded. Existing preferences will be preserved.</p>}
        {error && <p className="form-error" role="alert">{error}</p>}<div className="form-actions"><button type="button" className="button button-secondary" onClick={onCancel} disabled={pending}>Cancel</button>
        <button className="button button-primary" disabled={pending || !firstName.trim() || !lastName.trim()}>{pending ? 'Saving…' : 'Save customer'}</button></div></form>;
}
