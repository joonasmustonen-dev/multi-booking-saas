export function filterCatalog<T extends { name: string; active: boolean }>(
    items: T[],
    query: string,
    status: string,
    searchText?: (item: T) => string
) {
    const normalize = (value: string) =>
        value
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase();
    const text = normalize(query.trim());
    return items.filter(
        item =>
            (status === "all" || item.active === (status === "active")) &&
            normalize(`${item.name} ${searchText?.(item) ?? ""}`).includes(text)
    );
}
