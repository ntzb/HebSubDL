package com.nbs.hebsubdl.SubProviders;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ktuvit stores ImdbID in a 9-character column, so tt+8-digit ids arrive
 * truncated. Every title from roughly 2018 on is affected, which is why
 * The Bourne Supremacy (tt0372183) matched and The White Lotus
 * (tt13406094 -> tt1340609) never did. The JSON below is copied verbatim
 * from live SearchPage_search responses.
 */
class KtuvitSubProviderTest {

    private static JSONObject film(String json) {
        try {
            return (JSONObject) new JSONParser().parse(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final JSONObject WHITE_LOTUS = film("{"
            + "\"ID\":\"C8922AF781A9AE6CA09E5E9EBD217BCC\","
            + "\"EngName\":\"The White Lotus\",\"HebName\":\"הלוטוס הלבן\","
            + "\"IMDB_Link\":\"https://www.imdb.com/title/tt13406094/\","
            + "\"ReleaseDate\":\"2021\",\"IsSeries\":true,\"ImdbID\":\"tt1340609\"}");

    private static final JSONObject BOURNE = film("{"
            + "\"ID\":\"BOURNEID\",\"EngName\":\"The Bourne Supremacy\",\"HebName\":\"החזרה\","
            + "\"IMDB_Link\":\"https://www.imdb.com/title/tt0372183/\","
            + "\"ReleaseDate\":\"2004\",\"IsSeries\":false,\"ImdbID\":\"tt0372183\"}");

    private static final JSONObject NO_LINK = film("{"
            + "\"ID\":\"NOLINK\",\"EngName\":\"Severance\",\"HebName\":\"\","
            + "\"IMDB_Link\":null,\"ReleaseDate\":\"2022\",\"ImdbID\":\"tt1128074\"}");

    @Test
    void takesTheFullIdFromTheLinkNotTheTruncatedColumn() {
        assertEquals("tt13406094", KtuvitSubProvider.imdbIdOf(WHITE_LOTUS));
        assertEquals("tt0372183", KtuvitSubProvider.imdbIdOf(BOURNE));
    }

    @Test
    void fallsBackToTheColumnWhenTheLinkIsMissing() {
        assertEquals("tt1128074", KtuvitSubProvider.imdbIdOf(NO_LINK));
    }

    @Test
    void theRealWhiteLotusLookupNowMatches() {
        assertTrue(KtuvitSubProvider.imdbMatches(KtuvitSubProvider.imdbIdOf(WHITE_LOTUS), "tt13406094"));
    }

    @Test
    void truncatedColumnStillMatchesWhenThatIsAllWeHave() {
        assertTrue(KtuvitSubProvider.imdbMatches("tt1128074", "tt11280740"));
    }

    @Test
    void doesNotMatchADifferentTitle() {
        assertFalse(KtuvitSubProvider.imdbMatches("tt0372183", "tt13406094"));
        assertFalse(KtuvitSubProvider.imdbMatches("tt13406094", "tt1340609"),
                "a longer stored id must not match a shorter query");
    }

    @Test
    void emptyIdsNeverMatch() {
        assertFalse(KtuvitSubProvider.imdbMatches("", "tt13406094"));
        assertFalse(KtuvitSubProvider.imdbMatches("tt13406094", ""));
        assertFalse(KtuvitSubProvider.imdbMatches(null, "tt13406094"));
        assertFalse(KtuvitSubProvider.imdbMatches("tt13406094", null));
    }

    @Test
    void nameFallbackCoversAnEmptyImdbIdFromTheLookup() {
        assertTrue(KtuvitSubProvider.nameAndYearMatch(WHITE_LOTUS, "The White Lotus", null));
        assertTrue(KtuvitSubProvider.nameAndYearMatch(WHITE_LOTUS, "the.white.lotus", ""));
        assertTrue(KtuvitSubProvider.nameAndYearMatch(BOURNE, "The Bourne Supremacy", "2004"));
    }

    @Test
    void nameFallbackRejectsTheWrongTitleOrYear() {
        assertFalse(KtuvitSubProvider.nameAndYearMatch(WHITE_LOTUS, "The Bourne Supremacy", null));
        assertFalse(KtuvitSubProvider.nameAndYearMatch(BOURNE, "The Bourne Supremacy", "1988"));
        assertFalse(KtuvitSubProvider.nameAndYearMatch(WHITE_LOTUS, "", null),
                "an empty title must not match everything");
    }

    @Test
    void nameFallbackMatchesTheHebrewName() {
        assertTrue(KtuvitSubProvider.nameAndYearMatch(WHITE_LOTUS, "הלוטוס הלבן", null));
    }

    @Test
    void loginCookieIsFoundUnderCapitalizedHeaderAfterSessionCookie() {
        java.util.Map<String, java.util.List<String>> headers = new java.util.HashMap<>();
        headers.put(null, java.util.Collections.singletonList("HTTP/1.1 200 OK"));
        headers.put("Set-Cookie", java.util.Arrays.asList(
                "ASP.NET_SessionId=isg4dbvl4q1bklvfxwo1jrgz; path=/; HttpOnly",
                "Login=u=D97AB584&g=0CDB1D89; expires=Mon, 27-Oct-2026 17:52:00 GMT; path=/"));
        String cookie = KtuvitSubProvider.findLoginCookie(headers);
        assertNotNull(cookie);
        String[] parts = cookie.split(";");
        assertEquals("Login=u=D97AB584&g=0CDB1D89", parts[0].trim());
        assertEquals("Mon, 27-Oct-2026 17:52:00 GMT", KtuvitSubProvider.cookieAttribute(parts, "expires"));
    }

    @Test
    void noLoginCookieReturnsNull() {
        java.util.Map<String, java.util.List<String>> headers = new java.util.HashMap<>();
        headers.put("Set-Cookie", java.util.Collections.singletonList("ASP.NET_SessionId=x; path=/; HttpOnly"));
        assertNull(KtuvitSubProvider.findLoginCookie(headers));
    }

    @Test
    void renewedSessionReplacesTheStoredOne() {
        java.util.Map<String, String> jar = new java.util.LinkedHashMap<>();
        KtuvitSubProvider.mergeCookieHeader(jar, "Login=u=D97A&g=0CDB; ASP.NET_SessionId=old");
        KtuvitSubProvider.mergeCookieHeader(jar, "ASP.NET_SessionId=new");
        assertEquals("Login=u=D97A&g=0CDB; ASP.NET_SessionId=new", KtuvitSubProvider.cookieHeader(jar));
    }

    @Test
    void onlySubtitleAttachmentsCount() {
        assertEquals("srt", KtuvitSubProvider.subExtension(
                "attachment; filename=Thunderbolts.2025.720p.AMZN.WEB-DL.DDP5.1.H.264-KYOGO.srt"));
        assertEquals("srt", KtuvitSubProvider.subExtension("attachment; filename=\"a.b.SRT\""));
        // what a lost download request comes back as
        assertNull(KtuvitSubProvider.subExtension("attachment; filename=הבקשה לא נמצאה, נא לנסות להוריד את הקובץ בשנית"));
        assertNull(KtuvitSubProvider.subExtension(null));
        assertEquals("srt", KtuvitSubProvider.subExtension("attachment; filename=\"x.srt\"; size=123"));
        assertNull(KtuvitSubProvider.subExtension("attachment; filename=\"x.txt\"; note=a.srt"));
    }

    @Test
    void cookieExpiryIsReadAsGmt() {
        assertEquals(java.time.Instant.parse("2026-10-27T17:52:00Z").toEpochMilli(),
                KtuvitSubProvider.parseExpires("Tue, 27-Oct-2026 17:52:00 GMT"));
        assertEquals(java.time.Instant.parse("2026-10-27T17:52:00Z").toEpochMilli(),
                KtuvitSubProvider.parseExpires("Tue, 27 Oct 2026 17:52:00 GMT"));
    }

    @Test
    void unreadableExpiryFallsBackToADay() {
        long inADay = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
        assertTrue(Math.abs(KtuvitSubProvider.parseExpires("soon") - inADay) < 60_000);
        assertTrue(Math.abs(KtuvitSubProvider.parseExpires(null) - inADay) < 60_000);
    }
}
