import type { SelectOption } from "./SearchSelect";
const normalize = (value: string) =>
    value
        .normalize("NFD")
        .replace(/\p{Diacritic}/gu, "")
        .toLocaleLowerCase();
export const filterSelectOptions = (options: SelectOption[], query: string) =>
    options.filter(option =>
        normalize(`${option.label} ${option.description ?? ""}`).includes(
            normalize(query.trim())
        )
    );
export function nextEnabledOption(
    options: SelectOption[],
    current: number,
    direction: number
) {
    if (options.length === 0) return 0;
    let next = current;
    for (let i = 0; i < options.length; i++) {
        next = (next + direction + options.length) % options.length;
        if (!options[next].disabled) return next;
    }
    return current;
}
