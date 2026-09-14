export function formatTime(
    iso: string,
    timeZone: string
) {
    return new Intl.DateTimeFormat(
        "en-GB",
        {
            timeZone,
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
        }
    ).format(
        new Date(iso)
    );
}

export function formatDate(
    iso: string,
    timeZone: string
) {

    return new Intl.DateTimeFormat(
        undefined,
        {
            timeZone,
            year: "numeric",
            month: "short",
            day: "numeric",
        }
    ).format(
        new Date(iso)
    );
}