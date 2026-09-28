package com.nbs.hebsubdl.SubProviders;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThrottleTest {

    private static class FakeConnection extends HttpURLConnection {
        private final int status;
        private final Map<String, String> headers;

        FakeConnection(int status, Map<String, String> headers) throws IOException {
            super(new URL("http://example.invalid/"));
            this.status = status;
            this.headers = headers;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public String getHeaderField(String name) {
            return headers.get(name);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }

    @Test
    void requestsFromSeveralThreadsAreSpacedOut() throws Exception {
        Throttle throttle = new Throttle("test", 100);
        List<Long> starts = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    throttle.acquire();
                    synchronized (starts) {
                        starts.add(System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
            threads.add(t);
        }
        go.countDown();
        for (Thread t : threads)
            t.join();
        starts.sort(Long::compare);
        assertTrue(starts.get(2) - starts.get(0) >= 190, "3 requests at 100ms spacing span at least ~200ms: " + starts);
    }

    @Test
    void rateLimitedResponsesAreRetriedUntilTheyPass() throws Exception {
        Throttle throttle = new Throttle("test", 0);
        AtomicInteger calls = new AtomicInteger();
        HttpURLConnection con = throttle.send(() -> new FakeConnection(
                calls.incrementAndGet() < 3 ? 429 : 200, Map.of("Retry-After", "0")));
        assertEquals(200, con.getResponseCode());
        assertEquals(3, calls.get());
    }

    @Test
    void retriesAreBounded() throws Exception {
        Throttle throttle = new Throttle("test", 0);
        AtomicInteger calls = new AtomicInteger();
        HttpURLConnection con = throttle.send(() -> {
            calls.incrementAndGet();
            return new FakeConnection(503, Map.of("Retry-After", "0"));
        });
        assertEquals(503, con.getResponseCode(), "the last answer is handed back to the caller");
        assertEquals(Throttle.MAX_RETRIES + 1, calls.get());
    }

    @Test
    void otherErrorsAreNotRetried() throws Exception {
        Throttle throttle = new Throttle("test", 0);
        AtomicInteger calls = new AtomicInteger();
        throttle.send(() -> {
            calls.incrementAndGet();
            return new FakeConnection(500, Map.of());
        });
        assertEquals(1, calls.get());
    }

    @Test
    void theServersResetTimeIsHonoredAndCapped() throws Exception {
        assertEquals(3250, Throttle.retryDelayMs(new FakeConnection(429, Map.of("Retry-After", "3")), 1));
        assertEquals(2250, Throttle.retryDelayMs(new FakeConnection(429, Map.of("ratelimit-reset", "2")), 1));
        assertEquals(Throttle.MAX_BACKOFF_MS,
                Throttle.retryDelayMs(new FakeConnection(429, Map.of("Retry-After", "3600")), 1));
        // no hint: exponential
        assertEquals(2000, Throttle.retryDelayMs(new FakeConnection(429, Map.of()), 1));
        assertEquals(4000, Throttle.retryDelayMs(new FakeConnection(429, Map.of()), 2));
        // an HTTP-date Retry-After isn't parsed, so it falls back too
        assertEquals(2000, Throttle.retryDelayMs(new FakeConnection(429,
                Map.of("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")), 1));
    }
}
