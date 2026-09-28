package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

// Spaces out the requests to one site across all search threads, and backs off
// when the site says 429 or 503.
//
// OpenSubtitles documents 5 requests per second per IP (login: 1/s, 10/min).
// Ktuvit, Wizdom and IMDb publish nothing, so they get a gentle 2-3 per second.
public final class Throttle {
    public static final Throttle OPENSUBTITLES = new Throttle("OpenSubtitles", 250);
    public static final Throttle KTUVIT = new Throttle("Ktuvit", 400);
    public static final Throttle WIZDOM = new Throttle("Wizdom", 400);
    public static final Throttle IMDB = new Throttle("IMDb", 300);

    static final int MAX_RETRIES = 3;
    static final long MAX_BACKOFF_MS = 30_000;
    // without these a stalled socket blocks its search thread, and so the
    // whole run and every run queued behind it, forever
    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int READ_TIMEOUT_MS = 30_000;

    private final String name;
    private final long minIntervalMs;
    private long nextSlot = 0;

    Throttle(String name, long minIntervalMs) {
        this.name = name;
        this.minIntervalMs = minIntervalMs;
    }

    public interface Request {
        HttpURLConnection open() throws IOException;
    }

    public static <T extends URLConnection> T withTimeouts(T con) {
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        return con;
    }

    public void acquire() throws IOException {
        long wait;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextSlot);
            nextSlot = slot + minIntervalMs;
            wait = slot - now;
        }
        sleep(wait);
    }

    // holds everyone back, not just the thread that got the 429
    synchronized void pushBack(long ms) {
        nextSlot = Math.max(nextSlot, System.currentTimeMillis() + ms);
    }

    // Sends the request, retrying on 429/503. The returned connection has its
    // response code read already; null only if open() returned null.
    public HttpURLConnection send(Request request) throws IOException {
        for (int attempt = 1; ; attempt++) {
            acquire();
            HttpURLConnection con = request.open();
            if (con == null)
                return null;
            int status = con.getResponseCode();
            if (!isRateLimited(status) || attempt > MAX_RETRIES)
                return con;
            long delay = retryDelayMs(con, attempt);
            Logger.logger.warning(String.format("%s answered %d, waiting %d ms (retry %d of %d)",
                    name, status, delay, attempt, MAX_RETRIES));
            con.disconnect();
            pushBack(delay);
        }
    }

    static boolean isRateLimited(int status) {
        return status == 429 || status == 503;
    }

    // Retry-After is seconds; ratelimit-reset (OpenSubtitles) is seconds until
    // the window resets. Without either, back off exponentially.
    static long retryDelayMs(HttpURLConnection con, int attempt) {
        Long seconds = parseSeconds(con.getHeaderField("Retry-After"));
        if (seconds == null)
            seconds = parseSeconds(con.getHeaderField("ratelimit-reset"));
        long delay = seconds != null ? seconds * 1000 + 250 : 1000L << attempt;
        return Math.max(250, Math.min(delay, MAX_BACKOFF_MS));
    }

    static Long parseSeconds(String value) {
        if (value == null)
            return null;
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void sleep(long ms) throws IOException {
        if (ms <= 0)
            return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the rate limit", e);
        }
    }
}
