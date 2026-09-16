import {
    ArrowRight,
    BellRing,
    CalendarCheck2,
    CalendarDays,
    Check,
    Clock3,
    MapPin,
    Package,
    ShieldCheck,
    Sparkles,
    Users
} from "lucide-react";
import "./LandingPage.css";

const features = [
    [CalendarCheck2, "Complete combinations", "Match services with the staff, locations, and equipment needed to deliver them."],
    [Users, "Customer context", "Keep booking history, preferences, attendance, and operational notes close to every appointment."],
    [Clock3, "Real availability", "Combine recurring hours, weekly rotas, closures, time off, and extra availability."],
    [BellRing, "Cancellation waitlists", "Offer released capacity to matching customers and track each offer through its lifecycle."],
    [MapPin, "Multiple locations", "Assign teams to workplaces or let free agents work wherever a service is available."],
    [ShieldCheck, "Tenant-aware operations", "Separate workspace data and support privacy, retention, export, and erasure workflows."]
] as const;

export default function LandingPage() {
    return (
        <div className="landing-page">
            <div className="landing-preview-banner" role="note">
                <Sparkles size={15} />
                Development preview — MultiBooking is not a real commercial
                product or open for customer sign-ups.
            </div>

            <header className="landing-nav">
                <a className="landing-brand" href="/" aria-label="MultiBooking home">
                    <span className="landing-brand-mark"><CalendarDays size={20} /></span>
                    <span>MULTIBOOKING</span>
                </a>
                <nav aria-label="Homepage navigation">
                    <a href="#features">Features</a>
                    <a href="#workflow">How it works</a>
                    <a className="landing-sign-in" href="/app">
                        Open development app <ArrowRight size={16} />
                    </a>
                </nav>
            </header>

            <main>
                <section className="landing-hero">
                    <div className="landing-hero-copy">
                        <span className="landing-eyebrow">One workspace for every booking</span>
                        <h1>Make complex schedules feel <span>easy to book.</span></h1>
                        <p>
                            A general booking operations platform for teams,
                            locations, services, and shared resources. Plan
                            availability, manage customers, and keep the booking
                            lifecycle in one calm workspace.
                        </p>
                        <div className="landing-actions">
                            <a className="landing-primary" href="/app">
                                Explore the development app <ArrowRight size={18} />
                            </a>
                            <a className="landing-secondary" href="#features">
                                See what is implemented
                            </a>
                        </div>
                        <p className="landing-access-note">
                            Access requires a configured development account.
                            No purchases or public registrations are available.
                        </p>
                    </div>

                    <div className="landing-product-frame" aria-label="Booking dashboard preview">
                        <div className="preview-sidebar">
                            <div className="preview-logo"><CalendarDays size={17} /></div>
                            {[0, 1, 2, 3, 4].map(item => (
                                <span className={item === 0 ? "active" : ""} key={item} />
                            ))}
                        </div>
                        <div className="preview-workspace">
                            <div className="preview-topbar">
                                <div><small>MON, 14 SEP</small><strong>Good morning, Alice</strong></div>
                                <span>A</span>
                            </div>
                            <div className="preview-metrics">
                                <article className="mint"><CalendarDays size={16} /><div><strong>8</strong><small>Today&apos;s bookings</small></div></article>
                                <article className="blue"><Clock3 size={16} /><div><strong>24</strong><small>Open slots</small></div></article>
                                <article className="purple"><Users size={16} /><div><strong>142</strong><small>Customers</small></div></article>
                            </div>
                            <div className="preview-dashboard-grid">
                                <article className="preview-week">
                                    <div><strong>Bookings this week</strong><small>32 total bookings</small></div>
                                    <div className="preview-bars">
                                        {[52, 78, 42, 90, 68, 28, 18].map((height, index) => (
                                            <span key={index} style={{ height: `${height}%` }} />
                                        ))}
                                    </div>
                                </article>
                                <article className="preview-today">
                                    <strong>Today</strong>
                                    {[
                                        ["09:00", "Consultation", "Confirmed"],
                                        ["11:30", "Equipment hire", "Pending"],
                                        ["14:15", "Studio session", "Confirmed"]
                                    ].map(([time, service, status]) => (
                                        <div key={time}><b>{time}</b><span>{service}</span><small>{status}</small></div>
                                    ))}
                                </article>
                            </div>
                        </div>
                    </div>
                </section>

                <section className="landing-feature-section" id="features">
                    <div className="landing-section-heading">
                        <span className="landing-eyebrow">Built from real workflows</span>
                        <h2>Flexible enough for more than one kind of business.</h2>
                        <p>
                            Services define what a booking requires. The platform
                            coordinates the people, places, and things that can fulfil it.
                        </p>
                    </div>
                    <div className="landing-feature-grid">
                        {features.map(([Icon, title, text], index) => (
                            <article key={title} className={`tone-${(index % 3) + 1}`}>
                                <span><Icon size={20} /></span><h3>{title}</h3><p>{text}</p>
                            </article>
                        ))}
                    </div>
                </section>

                <section className="landing-workflow" id="workflow">
                    <div className="landing-workflow-copy">
                        <span className="landing-eyebrow">A connected workflow</span>
                        <h2>Set the rules once. Book with confidence.</h2>
                        <p>
                            Availability and appointment validation use the same
                            assignment model, so every offered combination is
                            checked again when it is saved.
                        </p>
                    </div>
                    <ol>
                        <li><span>01</span><div><strong>Define the service</strong><p>Choose duration, price, eligible assignments, and what is required.</p></div></li>
                        <li><span>02</span><div><strong>Plan availability</strong><p>Set regular schedules and adjust individual weeks or exceptions.</p></div></li>
                        <li><span>03</span><div><strong>Run the day</strong><p>Book, reschedule, filter the calendar, and recover cancelled capacity.</p></div></li>
                    </ol>
                </section>

                <section className="landing-status-card">
                    <div>
                        <span className="landing-eyebrow">Project status</span>
                        <h2>A working development project, not a launched product.</h2>
                        <p>
                            MultiBooking is being built and tested as a software
                            project. It currently has no public registration,
                            paid plans, support commitment, or production service
                            guarantee. Any data in the development app is test data.
                        </p>
                    </div>
                    <ul>
                        <li><Check size={17} /> Core booking operations implemented</li>
                        <li><Check size={17} /> Local and CI test environments</li>
                        <li><Package size={17} /> Production readiness still in progress</li>
                    </ul>
                </section>
            </main>

            <footer className="landing-footer">
                <a className="landing-brand" href="/">
                    <span className="landing-brand-mark"><CalendarDays size={18} /></span>
                    <span>MULTIBOOKING</span>
                </a>
                <p>Development preview · Not a commercial product</p>
                <a href="https://github.com/joonasmustonen-dev/multi-booking-saas" target="_blank" rel="noreferrer">View source</a>
            </footer>
        </div>
    );
}
