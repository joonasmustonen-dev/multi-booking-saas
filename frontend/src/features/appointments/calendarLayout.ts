import type {
    AppointmentCalendarItem,
} from "./appointmentTypes";


export interface PositionedAppointment {
    appointment: AppointmentCalendarItem;

    startMinute: number;
    endMinute: number;

    lane: number;
    laneCount: number;

    clusterId: number;
}


export interface AppointmentOverlapCluster {
    id: number;

    startMinute: number;
    endMinute: number;

    laneCount: number;

    appointments:
        AppointmentCalendarItem[];
}


export interface DayCalendarLayout {
    appointments:
        PositionedAppointment[];

    clusters:
        AppointmentOverlapCluster[];
}


export function dateKeyInTimeZone(
    iso: string,
    timeZone: string
) {

    const parts =
        new Intl.DateTimeFormat(
            "en-US",
            {
                timeZone,
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
            }
        ).formatToParts(
            new Date(iso)
        );


    const year =
        parts.find(
            part =>
                part.type === "year"
        )?.value;

    const month =
        parts.find(
            part =>
                part.type === "month"
        )?.value;

    const day =
        parts.find(
            part =>
                part.type === "day"
        )?.value;


    return `${year}-${month}-${day}`;
}


export function minutesInTimeZone(
    iso: string,
    timeZone: string
) {

    const parts =
        new Intl.DateTimeFormat(
            "en-US",
            {
                timeZone,

                hour:
                    "2-digit",

                minute:
                    "2-digit",

                hourCycle:
                    "h23",
            }
        ).formatToParts(
            new Date(iso)
        );


    const hour =
        Number(
            parts.find(
                part =>
                    part.type === "hour"
            )?.value ?? 0
        );


    const minute =
        Number(
            parts.find(
                part =>
                    part.type === "minute"
            )?.value ?? 0
        );


    return hour * 60 + minute;
}


export function layoutDayAppointments(
    appointments:
        AppointmentCalendarItem[],
    timeZone: string
): DayCalendarLayout {

    const ordered =
        appointments
            .map(
                appointment => ({
                    appointment,

                    startMinute:
                        minutesInTimeZone(
                            appointment.startAt,
                            timeZone
                        ),

                    endMinute:
                        minutesInTimeZone(
                            appointment.endAt,
                            timeZone
                        ),
                })
            )
            .sort(
                (first, second) => {

                    if (
                        first.startMinute
                        !==
                        second.startMinute
                    ) {
                        return (
                            first.startMinute
                            -
                            second.startMinute
                        );
                    }

                    return (
                        first.endMinute
                        -
                        second.endMinute
                    );
                }
            );


    /*
     * First divide appointments into overlap
     * clusters.
     *
     * A cluster can contain:
     *
     * A overlaps B
     * B overlaps C
     *
     * even if A does not directly overlap C.
     */
    const rawClusters:
        typeof ordered[] = [];


    let currentCluster:
        typeof ordered = [];

    let currentClusterEnd =
        -1;


    for (
        const appointment
        of ordered
    ) {

        if (
            currentCluster.length === 0
            ||
            appointment.startMinute
                <
                currentClusterEnd
        ) {

            currentCluster.push(
                appointment
            );

            currentClusterEnd =
                Math.max(
                    currentClusterEnd,
                    appointment.endMinute
                );

            continue;
        }


        rawClusters.push(
            currentCluster
        );

        currentCluster = [
            appointment,
        ];

        currentClusterEnd =
            appointment.endMinute;
    }


    if (
        currentCluster.length > 0
    ) {
        rawClusters.push(
            currentCluster
        );
    }


    const positioned:
        PositionedAppointment[] = [];

    const clusters:
        AppointmentOverlapCluster[] = [];


    rawClusters.forEach(
        (
            cluster,
            clusterId
        ) => {

            /*
             * Greedy lane assignment.
             *
             * Each lane stores the ending minute
             * of its most recent appointment.
             */
            const laneEnds:
                number[] = [];

            const laneAssignments =
                cluster.map(
                    item => {

                        let lane =
                            laneEnds
                                .findIndex(
                                    end =>
                                        end
                                        <=
                                        item.startMinute
                                );


                        if (
                            lane === -1
                        ) {
                            lane =
                                laneEnds.length;

                            laneEnds.push(
                                item.endMinute
                            );
                        } else {

                            laneEnds[lane] =
                                item.endMinute;
                        }


                        return {
                            ...item,
                            lane,
                        };
                    }
                );


            const laneCount =
                Math.max(
                    1,
                    laneEnds.length
                );


            for (
                const item
                of laneAssignments
            ) {

                positioned.push({
                    appointment:
                        item.appointment,

                    startMinute:
                        item.startMinute,

                    endMinute:
                        item.endMinute,

                    lane:
                        item.lane,

                    laneCount,

                    clusterId,
                });
            }


            clusters.push({
                id:
                    clusterId,

                startMinute:
                    Math.min(
                        ...cluster.map(
                            item =>
                                item.startMinute
                        )
                    ),

                endMinute:
                    Math.max(
                        ...cluster.map(
                            item =>
                                item.endMinute
                        )
                    ),

                laneCount,

                appointments:
                    cluster.map(
                        item =>
                            item.appointment
                    ),
            });
        }
    );


    return {
        appointments:
            positioned,

        clusters,
    };
}