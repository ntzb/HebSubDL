package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpensubtitlesNewSubProvider implements ISubProvider {
    private URL queryURL;
    private String language;
    private String apiKey;
    private String username;
    private String password;
    private String token;
    String chosenSubName;
    private boolean isMovie;
    private String baseURL = "https://api.opensubtitles.com/api/v1";
    long tokenValidity = 0;

    @Override
    public String getChosenSubName() {
        return chosenSubName;
    }

    OpensubtitlesNewSubProvider() {
        this.username = PropertiesClass.getOpenSubtitlesUsername().trim();
        this.password = PropertiesClass.getOpenSubtitlesPassword().trim();
        this.apiKey = PropertiesClass.getOpenSubtitlesApiKey().trim();
        if (this.username.isEmpty() || this.password.isEmpty() || this.apiKey.isEmpty()) {
            Logger.logger.fine("one or more OpenSubtitles parameters are missing.");
        }
    }

    private HttpURLConnection initConnection(String type, URL url, String data, HashMap<String, String> headers) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(type);
            for (String header : headers.keySet()) {
                con.setRequestProperty(header, headers.get(header));
            }

            if (data != null && !data.isEmpty()) {
                con.setDoOutput(true);
                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                out.writeBytes(data);
                out.flush();
                out.close();
            }
            return con;
        } catch (IOException e) {
            Logger.logException(e, "initializing http connection,");
            return null;
        }
    }

    private HashMap<String, String> getBasicHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
//        headers.put("User-Agent", "HebSubDL v1.2.0");
        headers.put("Content-Type", "application/json");
        headers.put("Api-Key", this.apiKey);
        return headers;
    }

    private boolean login() throws IOException {
        URL url = new URL(this.baseURL + "/login");
        String data = "{\"username\":\"" + this.username + "\",\"password\":\"" + this.password + "\"}";
        HashMap<String, String> headers = getBasicHeaders();
        HttpURLConnection con = initConnection("POST", url, data, headers);
        if (con == null)
            return false;

        int status = con.getResponseCode();
        if (status != 200) {
            Logger.logger.severe("login failed, error code is " + status);
            return false;
        }

        // check for error in login
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) content.append(inputLine);
        in.close();
        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(content.toString());
            Logger.logger.fine("got login result: " + obj.toJSONString());
            if ((long) obj.get("status") != 200) return false;
            // save the token and the validity time
            this.token = obj.get("token").toString();
            this.tokenValidity = Instant.now().getEpochSecond() + 24 * 60 * 60 - 60; // 24 hours minus 1 minute
        } catch (ParseException e) {
            Logger.logException(e, "parsing login response from Open Subtitles");
            return false;
        }
        return true;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        if (language.length() != 2) {
            Logger.logger.warning(String.format("expected 2 letter language code, but got %d letter language (code): %s," +
                    "will default to Hebrew.", language.length(), language));
            this.language = "he";
        } else {
            this.language = language;
        }
    }

    private boolean getIsMovie() {
        return isMovie;
    }

    private void setIsMovie(String episode) {
        this.isMovie = episode.equals("0");
    }

    @Override
    public URL getQueryURL() {
        return queryURL;
    }

    @Override
    public void setQueryURL(URL queryURL) {
        this.queryURL = queryURL;
    }

    private boolean isTokenValid() {
        return Instant.now().getEpochSecond() < this.tokenValidity;
    }

    private String buildURL(String base, String path, HashMap<String, String> queryParams) {
        StringBuilder url = new StringBuilder(base).append(path).append("?");
        AtomicInteger paramIndex = new AtomicInteger();
        queryParams.forEach((key, val) -> {
            if (paramIndex.get() > 0) url.append("&");
            url.append(key).append("=").append(val.replace(" ", "%20"));
            paramIndex.getAndIncrement();
        });
        return url.toString();
    }

    @Override
    public void generateQueryURL(MediaFile mediaFile) throws MalformedURLException {
        setLanguage(PropertiesClass.getLangSuffix().replace(".", ""));
        String imdbID = mediaFile.getImdbId().trim();
        String filename = mediaFile.getFileName().toLowerCase();
        String episode = mediaFile.getEpisode();
        String year = mediaFile.getYear(); // should we use it?
        HashMap<String, String> queryParams = new HashMap<>() {{
            put("ai_translated", "exclude");
            put("languages", getLanguage());
        }};
        if (!imdbID.isEmpty())
            queryParams.put("imdb_id", imdbID);
        else
            queryParams.put("query", filename);
        if (!episode.isEmpty() && !episode.equals("0")) {
            DecimalFormat formatter = new DecimalFormat("0");
            String formattedSeason = formatter.format(Integer.parseInt(mediaFile.getSeason()));
            String formattedEpisode = formatter.format(Integer.parseInt(mediaFile.getEpisode()));
            queryParams.put("season_number", formattedSeason);
            queryParams.put("episode_number", formattedEpisode);
        }
        String url = this.buildURL(this.baseURL, "/subtitles", queryParams);
        this.queryURL = new URL(url);
    }

    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        HashMap<String, String> headers = getBasicHeaders();
        HttpURLConnection con = initConnection("GET", url, null, headers);
        if (con == null) {
            Logger.logger.severe("failed initializing connection");
            return null;
        }

        int status = con.getResponseCode();
        if (status != 200) return null;

        // check for error in login
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) content.append(inputLine);
        in.close();

        return content.toString();
    }

    private String getDownloadURL(String subId) {
        URL url;
        try {
            url = new URL(this.baseURL + "/download");
        } catch (MalformedURLException e) {
            Logger.logger.severe("failed constructing URL for download link request");
            return null;
        }
        String data = "{\"file_id\":\"" + subId + "\"}";
        HashMap<String, String> headers = getBasicHeaders();
        headers.put("Authorization", "Bearer " + this.token);
        HttpURLConnection con = initConnection("POST", url, data, headers);
        if (con == null) {
            Logger.logger.severe("failed getting download link from OpenSubtitles");
            return null;
        }

        StringBuilder content = new StringBuilder();
        int status;
        try {
            status = con.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();
        } catch (IOException e) {
            Logger.logger.severe("failed getting response from OpenSubtitles");
            return null;
        }


        if (status != 200) {
            Logger.logger.severe("login failed, error code is " + status + ", content is: " + content);
            return null;
        }

        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(content.toString());
            Logger.logger.fine("got download result: " + obj.toJSONString());
            // save the token and the validity time
            return obj.get("link").toString();
        } catch (ParseException e) {
            Logger.logException(e, "parsing login response from Open Subtitles");
            return null;
        }
    }

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        // first, request URL for download
        String fileURL = getDownloadURL(subId);
        if (fileURL == null) {
            Logger.logger.severe("could not get download URL for OpenSubtitles");
            return false;
        }
        String subtitleExtension = FilenameUtils.getExtension(fileURL);
        URLConnection con = (new URL(fileURL)).openConnection();
        File subFile = new File(String.format("%s/%s.%s", mediaFile.getPathName(),
                mediaFile.getFileName(), subtitleExtension));

        try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); //try with resources
             FileOutputStream fos = new FileOutputStream(subFile)) {
            long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
            if (bytesTransferred == 0)
                return false;
        } catch (Exception e) {
            Logger.logException(e, "downloading subtitle");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        setLanguage(PropertiesClass.getLangSuffix().replace(".", ""));
        String[] ratingResponseArray = {"0", "0"};
        if (!this.isTokenValid()) {
            Logger.logger.info("OpenSubtitles not logged in, or token no longer valid");
            int tries = 5;
            boolean isLogin = this.login();
            while (!isLogin && tries > 0) {
                Logger.logger.severe("failed OpenSubtitles login, remaining tries: " + --tries);
                isLogin = this.login();
            }
            if (!isLogin) return ratingResponseArray;
        }
        setIsMovie(mediaFile.getEpisode());
        generateQueryURL(mediaFile);
        String queryResp = getQueryJsonResponse(getQueryURL());
        if (queryResp == null) {
            Logger.logger.severe("failed getting response from OpenSubtitles.");
            return ratingResponseArray;
        }
        List<SearchResults> results = getReleasesAndLinks(queryResp);
        ratingResponseArray = getTitleRating(results, titleWordsArray);
        return ratingResponseArray;
    }

    private List<SearchResults> getReleasesAndLinks(String jsonStr) {
        List<SearchResults> allMatches = new ArrayList<>();
        int flags = Pattern.CASE_INSENSITIVE /* | Pattern.LITERAL | Pattern.DOTALL | Pattern.UNICODE_CASE */;
        Pattern pattern = Pattern.compile("(\"release\":\"(?<release>.*?)\").*?(\"file_id\":(?<fileId>.*?),)", flags);
        Matcher matcher = pattern.matcher(jsonStr);
        while (matcher.find()) {
            String release = matcher.group("release").replaceAll("(?i).?heb", "");
            String fileId = matcher.group("fileId");
            SearchResults result = new SearchResults(release, fileId);
            allMatches.add(result);
        }
        return allMatches;
    }

    private String[] getTitleRating(List<SearchResults> searchResults, String[] titleWordsArray) {
        int maxRating = 0;
        String highestRatingLink = "";
        for (SearchResults result : searchResults) {
            String testedTitle = result.release.toLowerCase().trim();
            String[] testedTitleWordArray = testedTitle
                    .replaceAll("dd.{0,2}(2.{0,2}(0|1))", "dd20")
                    .replaceAll("dd.{0,2}(5.{0,2}(0|1))", "dd50")
                    .replace("web-dl", "webdl")
                    .replaceAll("_", " ").replaceAll
                            ("\\.", " ").replaceAll("-", " ").split(" ");
            int rating = 0;
            // bonus for english search
            if (this.language.equals("eng")) {
                // added bonus for non-hearing impaired subs when looking for English subs
                if (testedTitle.contains("nonhi"))
                    rating++;
            }
            for (String word : titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = result.fileId;
                chosenSubName = result.release;
            }
        }
        String[] titleRatingResponse = {highestRatingLink, String.valueOf(maxRating)};
        return titleRatingResponse;
    }

    class SearchResults {
        String release;
        String fileId;

        SearchResults(String release, String fileId) {
            this.release = release;
            this.fileId = fileId;
        }
    }
}
