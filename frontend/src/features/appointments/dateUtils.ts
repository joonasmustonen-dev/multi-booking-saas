export function addDays(date: string, amount: number) {
    const [year, month, day] = date.split("-").map(Number);

    const value = new Date(Date.UTC(year, month - 1, day + amount));

    return value.toISOString().slice(0, 10);
}

export function startOfWeek(date: string, weekStartsOn = 1) {
    const [year, month, day] = date.split("-").map(Number);

    const value = new Date(Date.UTC(year, month - 1, day));

    const weekDay = value.getUTCDay();

    const distanceFromMonday = (weekDay - weekStartsOn + 7) % 7;

    return addDays(date, -distanceFromMonday);
}
