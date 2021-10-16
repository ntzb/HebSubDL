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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class KtuvitSubProvider implements ISubProvider {

    @Override
    public String[] getRating (MediaFile mediaFile, String[] titleWordsArray) {
        String[] ratingResponseArray={"",""};
        this.dbAccess = new DbAccess();
        boolean ktuvitLoginValid = this.dbAccess.loginValid();
        try {
            if (!ktuvitLoginValid && !doLoginKtuvit()) {
                Logger.logger.warning("could not log in to Ktuvit, check your credentials");
                return ratingResponseArray;
            }
            String type = mediaFile.getEpisode().equals("0") ? "0" : "1";
            this.foundFilmID = initialSearch(type, mediaFile.getTitle(), mediaFile.getYear(), mediaFile.getImdbId());
            HashMap<String, String> foundSubs = subSearch(type, this.foundFilmID, mediaFile.getSeason(), mediaFile.getEpisode());
            ratingResponseArray = getTitleRating(foundSubs, titleWordsArray);
        } catch (Exception e) {
            Logger.logException(e, "getting subtitles and ratings for Ktuvit.");
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
        HttpURLConnection con = initConnection("POST", url, data, headers, false);
        if (con == null)
            return false;

        int status = con.getResponseCode();

        // check for error in login
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
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

            if (status != 200 || !(boolean)obj.get("IsSuccess"))
                return false;
        } catch (ParseException e) {
            Logger.logException(e, "parsing login response from Ktuvit");
            return false;
        }

        // we logged in, get the cookie and validity time
        String[] cookieArray = con.getHeaderFields().get("Set-Cookie").get(0).split(";");
        String cookie = cookieArray[0];
        //String cookie = cookieArray[0].split("=",2)[1];
        String validUntilStr = cookieArray[1].split("=",2)[1];
        DateTimeFormatter format = DateTimeFormatter.ofPattern("E, dd-LLL-yyyy HH:mm:ss z");
        LocalDateTime dateTime = LocalDateTime.parse(validUntilStr, format);
        long validUntil = dateTime.atZone(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli();
        //update the DB with cookie info
        return (this.dbAccess.insertLogin(cookie, validUntil));
    }

    private HttpURLConnection initConnection(String type, URL url, String data, HashMap<String, String> headers, boolean cookieNeeded) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(type);
            if (cookieNeeded) {
                String ourCookie = this.dbAccess.getCookie();
                con.setRequestProperty("Cookie", ourCookie);
                //works: con.setRequestProperty("Cookie", "ASP.NET_SessionId=t1u01gm255z2vxewddqqen4a; Login=u=D97AB58498264A1B771B6BA3302E89AB&g=0CDB1D895885C22247DC00D2698AB93C52F75FBA1ABB08652117057D3EBCAF56C5742E631716308196C9EBCB9C9634F1");
                //works: con.setRequestProperty("Cookie", "Login=u=D97AB58498264A1B771B6BA3302E89AB&g=0CDB1D895885C22247DC00D2698AB93C52F75FBA1ABB08652117057D3EBCAF56C5742E631716308196C9EBCB9C9634F1");
                //doesn't work: con.setRequestProperty("Cookie", "u=D97AB58498264A1B771B6BA3302E89AB&g=0CDB1D895885C22247DC00D2698AB93C52F75FBA1ABB08652117057D3EBCAF56C5742E631716308196C9EBCB9C9634F1");
            }

            //con.setRequestProperty("authority", "www.ktuvit.me");
            for(String header : headers.keySet()) {
                con.setRequestProperty(header, headers.get(header));
            }

            if (!data.isEmpty()) {
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
            for (int i = 0; i < ((JSONArray) obj.get("Films")).size(); i++) {
                String responseImdbID = ((JSONObject) ((JSONArray) obj.get("Films")).get(i)).get("ImdbID").toString();
                if (responseImdbID.equals(imdbId)) {
                    return ((JSONObject) ((JSONArray) obj.get("Films")).get(i)).get("ID").toString();
                }
            }
        } catch (ParseException e) {
            Logger.logException(e, "parsing JSON response for getting movie ID in Ktuvit");
            return null;
        }
        return null;
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
        headers.put("accept-encoding", "gzip, deflate, br");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("Referer", "https://www.ktuvit.me/MovieInfo.aspx?ID="+filmID);
        return headers;
    }

    private StringBuffer sendRequest(String requestType, String urlStr, String data, HashMap<String,String> headers, boolean cookieNeeded) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection con = initConnection(requestType, url, data, headers, cookieNeeded);
            if (con == null)
                return null;

            int status = con.getResponseCode();
            if (status == 200) {
                InputStream input = con.getInputStream();
                BufferedReader in;
                if (urlStr.contains("MovieInfo"))
                    in = new BufferedReader(new InputStreamReader(new GZIPInputStream(input)));
                else
                    in = new BufferedReader(new InputStreamReader(input));
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
            String[] testedTitleWordArray = testedTitle.replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = foundSubs.get(sub);
            }
        }
        return new String[]{highestRatingLink,String.valueOf(maxRating)};
    }

    @Override
    public boolean downloadSubFile(String subID, MediaFile mediaFile) {
        String downloadID;

        // first part - ask for download permission
        String data = "{\"request\":{\"FilmID\":\""+this.foundFilmID+"\",\"SubtitleID\":\""+subID+"\",\"FontSize\":0,\"FontColor\":\"\",\"PredefinedLayout\":-1}}";
        String urlStr = "https://www.ktuvit.me/Services/ContentProvider.svc/RequestSubtitleDownload";

        HashMap<String, String> headers = getBasicHeaders();
        headers.put("Referer", "https://www.ktuvit.me/MovieInfo.aspx?ID="+this.foundFilmID);
        StringBuffer response = sendRequest("POST", urlStr, data, headers, true);
        JSONParser jsonParser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) jsonParser.parse(response.toString());
            obj = (JSONObject) jsonParser.parse(obj.get("d").toString());
            downloadID = obj.get("DownloadIdentifier").toString();
        } catch (ParseException e) {
            Logger.logException(e, "parsing response for download request in Ktuvit");
            return false;
        }

        // second part - actually download
        headers = getDownloadHeaders(this.foundFilmID);
        try {
            URL url = new URL("https://www.ktuvit.me/Services/DownloadFile.ashx?DownloadIdentifier="+downloadID);
            HttpURLConnection con = initConnection("GET", url, "", headers, true);
            if (con == null)
                return false;

            String fileName = con.getHeaderField("Content-Disposition");
            File filePath = new File(mediaFile.getPathName() + "\\" +
                    FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + '.' + PropertiesClass.getLangSuffix() + '.' +
                    FilenameUtils.getExtension(fileName));
            long bytesTransferred = 0;
            try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); //try with resources
                 FileOutputStream fos = new FileOutputStream(filePath)) {
                bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
            }
            if (bytesTransferred == 0) {
                filePath.delete();
                return false;
            }
        } catch (IOException e) {
            Logger.logException(e, "downloading subtitle for Ktuvit");
        }

    return true;
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

    private DbAccess dbAccess;
    private String foundFilmID;
}
