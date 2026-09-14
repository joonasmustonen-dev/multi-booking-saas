import { useMemo, useState, type ReactNode } from 'react';
import { Search, List, LayoutGrid, ChevronLeft, ChevronRight } from 'lucide-react';
import { filterCatalog } from './catalogFilter';
export default function CatalogBrowser<T extends { id: string; name: string; active: boolean }>({ items, label, searchText, children }: {
    items: T[]; label: string; searchText?: (item: T) => string; children: (items: T[]) => ReactNode;
}) {
    const [query, setQuery] = useState(''); const [status, setStatus] = useState('all');
    const [view, setView] = useState<'list' | 'cards'>('list'); const [page, setPage] = useState(0);
    const filtered = useMemo(() => filterCatalog(items, query, status, searchText), [items, query, status, searchText]);
    const pageSize = 8; const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const current = Math.min(page, totalPages - 1); const visible = filtered.slice(current * pageSize, (current + 1) * pageSize);
    return <section className={`catalog-browser catalog-${view}`} aria-label={`${label} directory`}>
        <div className="catalog-toolbar"><label className="catalog-search"><Search size={17} /><input aria-label={`Search ${label.toLowerCase()}`} placeholder={`Search ${label.toLowerCase()}…`} value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} /></label>
            <div className="segmented-control" aria-label="Activity filter">{['all', 'active', 'inactive'].map(value => <button type="button" key={value} aria-pressed={status === value} onClick={() => { setStatus(value); setPage(0); }}>{value.charAt(0).toUpperCase() + value.slice(1)}</button>)}</div>
            <div className="segmented-control catalog-view"><button type="button" aria-label="List view" aria-pressed={view === 'list'} onClick={() => setView('list')}><List size={17} /></button><button type="button" aria-label="Card view" aria-pressed={view === 'cards'} onClick={() => setView('cards')}><LayoutGrid size={17} /></button></div></div>
        <p className="catalog-count">{filtered.length ? `${current * pageSize + 1}–${Math.min((current + 1) * pageSize, filtered.length)} of ${filtered.length}` : 'No matches'} {label.toLowerCase()}</p>
        {visible.length ? <div className={`catalog-items ${visible.length > 3 ? "catalog-items-scroll" : ""}`}>{children(visible)}</div> : <div className="catalog-empty">No matches. Try another search or activity filter.</div>}
        {totalPages > 1 && <nav className="catalog-pagination" aria-label={`${label} pages`}><button className="button button-secondary" disabled={current === 0} onClick={() => setPage(current - 1)}><ChevronLeft size={16} />Previous</button><span>Page {current + 1} of {totalPages}</span><button className="button button-secondary" disabled={current + 1 >= totalPages} onClick={() => setPage(current + 1)}>Next<ChevronRight size={16} /></button></nav>}
    </section>;
}
