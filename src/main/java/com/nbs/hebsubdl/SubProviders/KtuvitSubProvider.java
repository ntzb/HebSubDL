package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.DbAccess;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ReadableByteChannel;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class KtuvitSubProvider implements ISubProvider {

    private DbAccess dbAccess;
    private String foundFilmID;
    private String chosenSubName;
    // the download identifier lives in the ASP.NET session, so the session
    // cookie has to travel with Login, and be swapped when the server renews it
    private final Map<String, String> cookies = new LinkedHashMap<>();
    // each search thread has its own instance, but they share the stored
    // login, and only one of them should log in when it has expired
    private static final Object LOGIN_LOCK = new Object();
    boolean isHebrewOnly = true;

    @Override
    public String getChosenSubName() {
        return chosenSubName;
    }

    @Override
    public String[] getRating (MediaFile mediaFile, String[] titleWordsArray) {
        String[] ratingResponseArray={"0","0"};
        this.dbAccess = new DbAccess();
        this.cookies.clear();
        try {
            synchronized (LOGIN_LOCK) {
                if (this.dbAccess.loginValid()) {
                    // Login only: the stored session is shared by every search
                    // thread, and a pending download lives in the session, so
                    // each instance lets Ktuvit hand it a session of its own
                    mergeCookieHeader(this.cookies, this.dbAccess.getCookie());
                    this.cookies.remove("ASP.NET_SessionId");
                } else if (!doLoginKtuvit()) {
                    Logger.logger.warning("could not log in to Ktuvit, check your credentials");
                    return ratingResponseArray;
                }
            }
            String type = mediaFile.getEpisode().equals("0") ? "0" : "1";
            this.foundFilmID = initialSearch(type, mediaFile.getTitle(), mediaFile.getYear(), mediaFile.getImdbId());
            if (this.foundFilmID == null)
                return ratingResponseArray;
            HashMap<String, String> foundSubs = subSearch(type, this.foundFilmID, mediaFile.getSeason(), mediaFile.getEpisode());
            ratingResponseArray = getTitleRating(foundSubs, titleWordsArray);
        } catch (Exception e) {
            Logger.logException(e, "getting subtitles and ratings for Ktuvit.");
        } finally {
            this.dbAccess.close();
        }
        return ratingResponseArray;
    }

    // login to ktuvit, and fill the login info into the DB
    private boolean doLoginKtuvit() throws IOException {
        String username = PropertiesClass.getKtuvitUsername();
        String password = PropertiesClass.getKtuvitPassword();
        URL url = new URL("https://www.ktuvit.me/Services/MembershipService.svc/Login");
        String data = "{\"request\":{\"Email\":\"" + username + "\",\"Password\":\"" + password + "\"}}";
        HashMap<String, String> headers = getBasicHeaders();
        HttpURLConnection con = Throttle.KTUVIT.send(() -> initConnection("POST", url, data, headers, false));
        if (con == null)
            return false;

        int status = con.getResponseCode();
        this.cookies.clear();
        storeCookies(con);

        // check for error in login
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(content.toString());
            obj = (JSONObject) jsonParser.parse(obj.get("d").toString());

            if (status != 200 || !(boolean)obj.get("IsSuccess")) {
                Logger.logger.warning("Ktuvit login rejected, status " + status + ": " + obj.get("ErrorMessage"));
                return false;
            }
        } catch (ParseException e) {
            Logger.logException(e, "parsing login response from Ktuvit");
            return false;
        }

        // we logged in, get the cookie and validity time
        String loginCookie = findLoginCookie(con.getHeaderFields());
        if (loginCookie == null) {
            Logger.logger.warning("Ktuvit login succeeded but no Login cookie came back, got: " + setCookies(con.getHeaderFields()));
            return false;
        }
        String[] cookieArray = loginCookie.split(";");
        long validUntil = parseExpires(cookieAttribute(cookieArray, "expires"));
        //update the DB with cookie info
        return (this.dbAccess.insertLogin(cookieHeader(this.cookies), validUntil));
    }

    // an expiry we can't read shouldn't lose a good login, so assume a day
    static long parseExpires(String expires) {
        if (expires != null) {
            for (String pattern : new String[]{"E, dd-LLL-yyyy HH:mm:ss z", "E, dd LLL yyyy HH:mm:ss z"}) {
                try {
                    DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                    return ZonedDateTime.parse(expires, format).toInstant().toEpochMilli();
                } catch (DateTimeParseException ignored) {
                }
            }
            Logger.logger.warning("could not parse the Ktuvit cookie expiry '" + expires + "', assuming a day");
        }
        return System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
    }

    private void storeCookies(HttpURLConnection con) {
        for (String setCookie : setCookies(con.getHeaderFields()))
            mergeCookieHeader(this.cookies, setCookie.split(";", 2)[0]);
    }

    // accepts both a Cookie request header ("a=1; b=2") and the name=value
    // part of a single Set-Cookie
    static void mergeCookieHeader(Map<String, String> jar, String header) {
        if (header == null)
            return;
        for (String pair : header.split(";")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && !kv[0].isEmpty())
                jar.put(kv[0], kv[1]);
        }
    }

    static String cookieHeader(Map<String, String> jar) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> cookie : jar.entrySet()) {
            if (sb.length() > 0)
                sb.append("; ");
            sb.append(cookie.getKey()).append("=").append(cookie.getValue());
        }
        return sb.toString();
    }

    // HttpURLConnection's header map is case-sensitive, and Ktuvit sends
    // ASP.NET_SessionId ahead of the Login cookie we actually need
    static List<String> setCookies(Map<String, List<String>> headers) {
        List<String> cookies = new ArrayList<>();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if ("set-cookie".equalsIgnoreCase(header.getKey()))
                cookies.addAll(header.getValue());
        }
        return cookies;
    }

    static String findLoginCookie(Map<String, List<String>> headers) {
        for (String cookie : setCookies(headers)) {
            if (cookie.trim().startsWith("Login="))
                return cookie;
        }
        return null;
    }

    static String cookieAttribute(String[] cookieParts, String name) {
        for (int i = 1; i < cookieParts.length; i++) {
            String[] kv = cookieParts[i].trim().split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(name))
                return kv[1].trim();
        }
        return null;
    }

    private HttpURLConnection initConnection(String type, URL url, String data, HashMap<String, String> headers, boolean cookieNeeded) {
        try {
            HttpURLConnection con = Throttle.withTimeouts((HttpURLConnection) url.openConnection());
            con.setRequestMethod(type);
            if (cookieNeeded && !this.cookies.isEmpty())
                con.setRequestProperty("cookie", cookieHeader(this.cookies));

            //con.setRequestProperty("authority", "www.ktuvit.me");
            for(String header : headers.keySet()) {
                con.setRequestProperty(header, headers.get(header));
            }

            if (!data.isEmpty()) {
                con.setDoOutput(true);
                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                out.write(data.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            }
            return con;
        } catch (IOException e) {
            Logger.logException(e, "initializing http connection,");
            return null;
        }
    }

    private String initialSearch(String type, String title, String year, String imdbId) {
        String data = "{\"request\":{\"FilmName\":\"" + title + "\",\"Actors\":[],\"Studios\":null,\"Directors\":[]," +
                "\"Genres\":[],\"Countries\":[],\"Languages\":[],\"Year\":\"" + (year != null ? year : "") +
                "\",\"Rating\":[],\"Page\":1," + "\"SearchType\":\"" + type + "\",\"WithSubsOnly\":false}}";
        String url = "https://www.ktuvit.me/Services/ContentProvider.svc/SearchPage_search";

        HashMap<String, String> headers = getBasicHeaders();
        StringBuffer response = sendRequest("POST", url, data, headers, false);
        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(response.toString());
            obj = (JSONObject) jsonParser.parse(obj.get("d").toString());
            JSONArray films = (JSONArray) obj.get("Films");
            StringBuilder seen = new StringBuilder();
            for (Object filmObj : films) {
                JSONObject film = (JSONObject) filmObj;
                String filmImdbId = imdbIdOf(film);
                seen.append(filmImdbId).append("/").append(nameOf(film)).append(" ");
                if (imdbMatches(filmImdbId, imdbId))
                    return film.get("ID").toString();
            }
            for (Object filmObj : films) {
                JSONObject film = (JSONObject) filmObj;
                if (nameAndYearMatch(film, title, year)) {
                    Logger.logger.info("Ktuvit: no imdb match, falling back to name+year for " + nameOf(film));
                    return film.get("ID").toString();
                }
            }
            Logger.logger.warning("Ktuvit: no match for imdb id '" + imdbId + "', title '" + title
                    + "', year '" + year + "'. candidates: " + (seen.length() == 0 ? "(none)" : seen.toString().trim()));
        } catch (ParseException e) {
            Logger.logException(e, "parsing JSON response for getting movie ID in Ktuvit");
            return null;
        }
        return null;
    }

    // Ktuvit's ImdbID column holds 9 chars, so tt+8-digit ids arrive truncated
    // (tt13406094 -> tt1340609). IMDB_Link carries the full id.
    static String imdbIdOf(JSONObject film) {
        Object link = film.get("IMDB_Link");
        if (link != null) {
            Matcher matcher = Pattern.compile("(tt\\d+)").matcher(link.toString());
            if (matcher.find())
                return matcher.group(1);
        }
        Object id = film.get("ImdbID");
        return id == null ? "" : id.toString();
    }

    static boolean imdbMatches(String theirs, String ours) {
        if (theirs == null || ours == null || theirs.isEmpty() || ours.isEmpty())
            return false;
        return theirs.equals(ours) || (ours.length() > theirs.length() && ours.startsWith(theirs));
    }

    static String nameOf(JSONObject film) {
        Object eng = film.get("EngName");
        if (eng != null && !eng.toString().isEmpty())
            return eng.toString();
        Object heb = film.get("HebName");
        return heb == null ? "" : heb.toString();
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static boolean nameAndYearMatch(JSONObject film, String title, String year) {
        String wanted = normalize(title);
        if (wanted.isEmpty())
            return false;
        if (!wanted.equals(normalize(nameOf(film))) && !wanted.equals(normalize(strOf(film.get("HebName")))))
            return false;
        if (year == null || year.isEmpty())
            return true;
        String released = strOf(film.get("ReleaseDate"));
        return released.isEmpty() || released.startsWith(year);
    }

    static String strOf(Object o) {
        return o == null ? "" : o.toString();
    }

    private HashMap<String, String> getBasicHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("authority", "www.ktuvit.me");
        headers.put("accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("x-requested-with", "XMLHttpRequest");
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML like Gecko) Chrome/85.0.4183.121 Safari/537.36");
        headers.put("content-type", "application/json");
        headers.put("origin", "https,//www.ktuvit.me");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-dest", "empty");
        headers.put("accept-language", "en-US,en;q=0.9");
        return headers;
    }

    private HashMap<String, String> getDownloadHeaders(String filmID) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("authority", "www.ktuvit.me");
        headers.put("Referer", "https://www.ktuvit.me/MovieInfo.aspx?ID="+filmID);
        headers.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        headers.put("accept-encoding", "gzip, deflate, br");
        headers.put("accept-language", "en-US,en;q=0.9,he;q=0.8");
        headers.put("cache-control", "no-cache");
        headers.put("pragma", "no-cache");
        headers.put("sec-fetch-dest", "document");
        headers.put("sec-fetch-mode", "navigate");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("upgrade-insecure-requests", "1");
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36");
        return headers;
    }

    private HashMap<String, String> getMovieHeaders(String filmID) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("authority", "www.ktuvit.me");
        headers.put("cache-control", "no-cache");
        headers.put("pragma", "no-cache");
        headers.put("upgrade-insecure-requests", "1");
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36");
        headers.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("sec-fetch-mode", "navigate");
        headers.put("sec-fetch-user", "?1");
        headers.put("sec-fetch-dest", "document");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua", "\"Chromium\";v=\"92\", \" Not A;Brand\";v=\"99\", \"Google Chrome\";v=\"92\"");
        headers.put("accept-encoding", "gzip, deflate");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("referer", "https://www.ktuvit.me/MovieInfo.aspx?ID="+filmID);
        return headers;
    }

    private StringBuffer sendRequest(String requestType, String urlStr, String data, HashMap<String,String> headers, boolean cookieNeeded) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection con = Throttle.KTUVIT.send(() -> initConnection(requestType, url, data, headers, cookieNeeded));
            if (con == null)
                return null;

            int status = con.getResponseCode();
            storeCookies(con);
            if (status == 200) {
                InputStream input = con.getInputStream();
                BufferedReader in;
                String encoding = con.getHeaderField("content-encoding");
                if (encoding != null && encoding.equals("gzip"))
                    in = new BufferedReader(new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8));
                else
                    in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                String inputLine;
                StringBuffer content = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                return content;
            } else {
                return null;
            }
        } catch (Exception e) {
            Logger.logException(e, "sending http request");
            return null;
        }
    }

    private HashMap<String, String> subSearch(String type, String filmID, String season, String episode) {
        String url;
        String urlParams;
        HashMap<String, String> headers;
        if (type.equals("0")) {
            // movie
            url = "https://www.ktuvit.me/MovieInfo.aspx?";
            urlParams = String.format("ID=%1$s", filmID);
            headers = getMovieHeaders(filmID);
        }
        else {
            // tvshow
            url = "https://www.ktuvit.me/Services/GetModuleAjax.ashx";
            urlParams = String.format("?moduleName=SubtitlesList&SeriesID=%1$s&Season=%2$s&Episode=%3$s", filmID, season, episode);
            headers = getBasicHeaders();
        }
        url = url+urlParams;

        StringBuffer response = sendRequest("GET", url, "", headers, true);

        HashMap<String, String> allMatches = new HashMap<>();
        Matcher matcher = Pattern.compile("<tr>(.+?)</tr>").matcher(response.toString());
        while (matcher.find()) {
            String aMatch = matcher.group();
            Matcher subInfoMatcher = Pattern.compile("<div style=\"float.+?>(.+?)<br />.+?data-subtitle-id=\"(.+?)\"").matcher(aMatch);
            if (!subInfoMatcher.find())
                continue;
            String subName = subInfoMatcher.group(1).replace("\n","").replace("\r","").replace("\t","").replace(" ","");
            String subId = subInfoMatcher.group(2);
            allMatches.put(subName, subId);
        }
        return allMatches;
    }

    private String[] getTitleRating(HashMap<String, String> foundSubs, String[] titleWordsArray) {
        int maxRating = 0;
        String highestRatingLink ="";
        for(String sub : foundSubs.keySet()) {
            String testedTitle = sub.toLowerCase().trim();
            String[] testedTitleWordArray = testedTitle
                    .replaceAll("dd.{0,2}(2.{0,2}(0|1))", "dd20")
                    .replaceAll("dd.{0,2}(5.{0,2}(0|1))", "dd50")
                    .replace("web-dl","webdl")
                    .replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = foundSubs.get(sub);
                chosenSubName = sub;
            }
        }
        return new String[]{highestRatingLink,String.valueOf(maxRating)};
    }

    // The download identifier seems to live in one backend's in-memory session,
    // so the follow-up GET finds it only when it's routed to the same server.
    // Live, most downloads land first time, but losses come in streaks - up to
    // three in a row - and waiting before the GET only makes it worse.
    private static final int DOWNLOAD_ATTEMPTS = 5;

    @Override
    public boolean downloadSubFile(String subID, MediaFile mediaFile) {
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            Boolean downloaded = tryDownloadSubFile(subID, mediaFile);
            if (downloaded != null)
                return downloaded;
            Logger.logger.info("Ktuvit lost download request " + attempt + " of " + DOWNLOAD_ATTEMPTS);
        }
        return false;
    }

    // null means Ktuvit lost the request, the only failure worth retrying
    private Boolean tryDownloadSubFile(String subID, MediaFile mediaFile) {
        String downloadID;

        // first part - ask for download permission
        String data = "{\"request\":{\"FilmID\":\""+this.foundFilmID+"\",\"SubtitleID\":\""+subID+"\",\"FontSize\":0,\"FontColor\":\"\",\"PredefinedLayout\":-1}}";
        String urlStr = "https://www.ktuvit.me/Services/ContentProvider.svc/RequestSubtitleDownload";

        HashMap<String, String> headers = getBasicHeaders();
        headers.put("Referer", "https://www.ktuvit.me/MovieInfo.aspx?ID="+this.foundFilmID);
        StringBuffer response = sendRequest("POST", urlStr, data, headers, true);
        if (response == null)
            return false;
        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(response.toString());
            obj = (JSONObject) jsonParser.parse(obj.get("d").toString());
            Logger.logger.fine("Ktuvit download request: " + obj.toJSONString());
            Object id = obj.get("DownloadIdentifier");
            if (id == null || id.toString().isEmpty()) {
                Logger.logger.warning("Ktuvit refused the download request: " + obj.get("ErrorMessage"));
                return false;
            }
            downloadID = id.toString();
        } catch (ParseException e) {
            Logger.logException(e, "parsing response for download request in Ktuvit");
            return false;
        }

        // second part - actually download
        headers = getDownloadHeaders(this.foundFilmID);
        try {
            URL url = new URL("https://www.ktuvit.me/Services/DownloadFile.ashx?DownloadIdentifier="+downloadID);
            // not throttled: any delay after the request loses the identifier
            HttpURLConnection con = initConnection("GET", url, "", headers, true);
            if (con == null)
                return false;
            if (con.getResponseCode() != 200) {
                Logger.logger.warning("Ktuvit download failed with status " + con.getResponseCode());
                return false;
            }

            // a lost download request still comes back as a 200 attachment, but
            // it's a text file named after the Hebrew error message
            String extension = subExtension(con.getHeaderField("Content-Disposition"));
            if (extension == null) {
                String body = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Logger.logger.warning("Ktuvit sent no subtitle file: " + body.trim());
                return null;
            }
            File filePath = new File(String.format("%s/%s%s.%s", mediaFile.getPathName(),
                    FilenameUtils.removeExtension(mediaFile.getOriginalFileName()), PropertiesClass.getLangSuffix(),
                    extension));
            long bytesTransferred = 0;
            try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); //try with resources
                 FileOutputStream fos = new FileOutputStream(filePath)) {
                bytesTransferred = fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (IOException e) {
                // a partial file would make subAlreadyExists skip this video for good
                filePath.delete();
                throw e;
            }
            if (bytesTransferred == 0) {
                filePath.delete();
                return false;
            }
        } catch (IOException e) {
            Logger.logException(e, "downloading subtitle for Ktuvit");
            return false;
        }

    return true;
    }

    static String subExtension(String contentDisposition) {
        if (contentDisposition == null)
            return null;
        Matcher matcher = Pattern.compile("(?i)(?:^|;)\\s*filename\\s*=\\s*(\"[^\"]*\"|[^;]*)").matcher(contentDisposition);
        if (!matcher.find())
            return null;
        String fileName = matcher.group(1).trim().replaceAll("^\"|\"$", "");
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        return extension.equals("srt") || extension.equals("sub") ? extension : null;
    }

    @Override
    public URL getQueryURL() {
        return null;
    }

    @Override
    public void setQueryURL(URL queryURL) {

    }

    @Override
    public void generateQueryURL(MediaFile mediaFile) {

    }

    @Override
    public String getQueryJsonResponse(URL url) {
        return null;
    }
}
