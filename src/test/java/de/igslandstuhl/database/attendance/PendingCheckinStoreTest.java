package de.igslandstuhl.database.attendance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PendingCheckinStoreTest {
    @Test void pendingSurvivesLoginAndIsConsumedExactlyOnce() throws Exception {
        var store = new PendingCheckinStore(Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC));
        store.remember("browser-session", 7);
        assertEquals(PendingCheckinStore.State.ACTIVE, store.state("browser-session"));
        assertEquals(Long.valueOf(7), store.consume("browser-session", id -> id));
        assertEquals(PendingCheckinStore.State.NONE, store.state("browser-session"));
        assertThrows(IllegalArgumentException.class, () -> store.consume("browser-session", id -> id));
    }

    @Test void expiredPendingIsDiscarded() {
        var start = Instant.parse("2026-08-29T12:00:00Z");
        var mutable = new MutableClock(start);
        var store = new PendingCheckinStore(mutable);
        store.remember("browser-session", 7);
        mutable.now = start.plus(PendingCheckinStore.TTL);
        assertEquals(PendingCheckinStore.State.EXPIRED, store.state("browser-session"));
        assertEquals(PendingCheckinStore.State.NONE, store.state("browser-session"));
    }

    @Test void failedCheckinRemainsPendingAndCannotCreateAWriteByItself() {
        var store = new PendingCheckinStore(Clock.systemUTC());
        var writes = new AtomicInteger();
        store.remember("browser-session", 7);
        assertThrows(Exception.class, () -> store.consume("browser-session", id -> { throw new Exception("db unavailable"); }));
        assertEquals(0, writes.get());
        assertEquals(PendingCheckinStore.State.ACTIVE, store.state("browser-session"));
    }

    @Test void sessionsAreIsolatedAndNoRedirectIsStored() throws Exception {
        var store = new PendingCheckinStore(Clock.systemUTC());
        store.remember("browser-a", 9);
        assertEquals(PendingCheckinStore.State.NONE, store.state("browser-b"));
        assertEquals(Long.valueOf(9), store.consume("browser-a", id -> id));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
