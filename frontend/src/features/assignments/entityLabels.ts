export interface NamedEntity {
    id: string;
    name: string;
    email?: string | null;
    phone?: string | null;
    addressLine?: string | null;
    city?: string | null;
}
export function entityLabel(item: NamedEntity, items: readonly NamedEntity[]) {
    const matches = items.filter(
        other =>
            other.name.trim().toLocaleLowerCase() ===
            item.name.trim().toLocaleLowerCase()
    );
    if (matches.length < 2) return item.name;
    const detail =
        item.email ||
        item.phone ||
        [item.addressLine, item.city].filter(Boolean).join(", ");
    const suffix = item.id.slice(-8);
    const id = matches.some(
        other => other.id !== item.id && other.id.endsWith(suffix)
    )
        ? item.id
        : suffix;
    return `${item.name} · ${detail ? detail + " · " : ""}${id}`;
}
