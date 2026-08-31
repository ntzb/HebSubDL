package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.PropertiesClass;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Providers copy their credentials in the constructor and cache a JWT for 24h,
 * and FindSubs holds them in a static list built once at startup. Saving new
 * settings therefore had no effect until the app was restarted.
 */
class ProviderCredentialReloadTest {

    private static String field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(target);
    }

    private static void setCredentials(String key, String user) {
        PropertiesClass.setOpenSubtitlesApiKey(key);
        PropertiesClass.setOpenSubtitlesUsername(user);
        PropertiesClass.setOpenSubtitlesPassword("pw");
        PropertiesClass.setOpenSubtitlesUserAgent("ua v1");
    }

    @Test
    void aLiveProviderKeepsTheOldCredentialsAfterSettingsChange() throws Exception {
        setCredentials("OLD_KEY", "olduser");
        OpensubtitlesNewSubProvider live = new OpensubtitlesNewSubProvider();
        assertEquals("OLD_KEY", field(live, "apiKey"));

        setCredentials("NEW_KEY", "newuser");
        assertEquals("OLD_KEY", field(live, "apiKey"),
                "this staleness is the bug - the instance never re-reads");
    }

    @Test
    void aRebuiltProviderPicksUpTheNewCredentials() throws Exception {
        setCredentials("OLD_KEY", "olduser");
        FindSubs.reinitProviders();

        setCredentials("NEW_KEY", "newuser");
        FindSubs.reinitProviders();

        OpensubtitlesNewSubProvider rebuilt = FindSubs.providersList.stream()
                .filter(p -> p instanceof OpensubtitlesNewSubProvider)
                .map(p -> (OpensubtitlesNewSubProvider) p)
                .findFirst().orElseThrow();

        assertEquals("NEW_KEY", field(rebuilt, "apiKey"));
        assertEquals("newuser", field(rebuilt, "username"));
    }

    @Test
    void aRebuiltProviderHasNoCachedToken() throws Exception {
        setCredentials("KEY", "user");
        FindSubs.reinitProviders();
        OpensubtitlesNewSubProvider p = FindSubs.providersList.stream()
                .filter(x -> x instanceof OpensubtitlesNewSubProvider)
                .map(x -> (OpensubtitlesNewSubProvider) x)
                .findFirst().orElseThrow();

        assertNull(field(p, "token"), "a rebuilt provider must not carry the old JWT");
        assertEquals(0, p.tokenValidity, "token validity must reset, or isTokenValid() short-circuits login");
    }

    @Test
    void rebuildingReplacesTheInstanceRatherThanAppending() {
        setCredentials("KEY", "user");   // the constructors trim(), so unset properties NPE
        FindSubs.reinitProviders();
        int size = FindSubs.providersList.size();
        Object before = FindSubs.providersList.get(0);

        FindSubs.reinitProviders();

        assertEquals(size, FindSubs.providersList.size(), "providers accumulated instead of being replaced");
        assertNotSame(before, FindSubs.providersList.get(0));
    }
}
