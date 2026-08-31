package com.nbs.hebsubdl.SubProviders;

import org.junit.jupiter.api.Test;

import static com.nbs.hebsubdl.SubProviders.OpensubtitlesNewSubProvider.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * login() used to collapse every failure to false, so a 403 was retried five
 * times in under a second. The log from 2026-08-29 shows the result: one 403
 * followed by five 429s, with the rate limit then blocking the next
 * legitimate attempt.
 */
class OpensubtitlesNewSubProviderTest {

    @Test
    void badCredentialsAreNotRetried() {
        assertFalse(isTransientLoginFailure(401));
        assertFalse(isTransientLoginFailure(403));
    }

    @Test
    void rateLimitAndServerErrorsAreRetried() {
        assertTrue(isTransientLoginFailure(429));
        assertTrue(isTransientLoginFailure(500));
        assertTrue(isTransientLoginFailure(502));
        assertTrue(isTransientLoginFailure(503));
    }

    @Test
    void aDeadConnectionIsRetried() {
        assertTrue(isTransientLoginFailure(LOGIN_NO_CONNECTION));
    }

    @Test
    void anUnparseableResponseIsNotRetried() {
        assertFalse(isTransientLoginFailure(LOGIN_BAD_RESPONSE));
    }

    @Test
    void otherClientErrorsAreNotRetried() {
        assertFalse(isTransientLoginFailure(400));
        assertFalse(isTransientLoginFailure(404));
        assertFalse(isTransientLoginFailure(422));
    }

    @Test
    void successIsNotAFailure() {
        assertFalse(isTransientLoginFailure(LOGIN_OK));
    }

    @Test
    void backoffIsBoundedAndLeavesRoomForOneRetry() {
        assertTrue(LOGIN_MAX_ATTEMPTS >= 2, "no retry at all");
        assertTrue(LOGIN_MAX_ATTEMPTS <= 5, "too many attempts against a rate limit");
        assertTrue(LOGIN_RETRY_BASE_MS >= 500, "a sub-second retry is what earned the 429s");
    }
}
