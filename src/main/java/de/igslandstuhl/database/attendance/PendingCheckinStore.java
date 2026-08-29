package de.igslandstuhl.database.attendance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class PendingCheckinStore {
    static final Duration TTL = Duration.ofMinutes(10);

    @FunctionalInterface
    interface CheckinAction<T> {
        T run(long locationId) throws Exception;
    }

    enum State { NONE, ACTIVE, EXPIRED }

    private record Pending(long locationId, Instant expiresAt) {}

    private final Clock clock;
    private final Map<String, Pending> entries = new HashMap<>();

    PendingCheckinStore(Clock clock) {
        this.clock = clock;
    }

    synchronized void remember(String browserSession, long locationId) {
        requireSession(browserSession);
        purgeExpired();
        entries.put(browserSession, new Pending(locationId, clock.instant().plus(TTL)));
    }

    synchronized State state(String browserSession) {
        if (browserSession == null || browserSession.isBlank()) return State.NONE;
        Pending pending = entries.get(browserSession);
        if (pending == null) return State.NONE;
        if (!pending.expiresAt().isAfter(clock.instant())) {
            entries.remove(browserSession);
            return State.EXPIRED;
        }
        return State.ACTIVE;
    }

    synchronized void discard(String browserSession) {
        if (browserSession != null) entries.remove(browserSession);
    }

    synchronized <T> T consume(String browserSession, CheckinAction<T> action) throws Exception {
        State current = state(browserSession);
        if (current != State.ACTIVE) {
            throw new IllegalArgumentException(current == State.EXPIRED
                    ? "Dein gemerkter Check-in ist abgelaufen. Bitte scanne den aktuellen QR-Code am Raum erneut."
                    : "Es ist kein Check-in zum Fortsetzen vorhanden.");
        }
        Pending pending = entries.remove(browserSession);
        try {
            return action.run(pending.locationId());
        } catch (Exception e) {
            if (pending.expiresAt().isAfter(clock.instant())) entries.put(browserSession, pending);
            throw e;
        }
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static void requireSession(String browserSession) {
        if (browserSession == null || browserSession.isBlank()) {
            throw new IllegalArgumentException("Browser-Sitzung fehlt");
        }
    }
}
