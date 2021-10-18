package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class SubscenterSubProvider implements ISubProvider {
    private final String BASE_URL = "http://www.cinemast.org/he/cinemast/api/";
    private URL queryUrl;
    private StringBuilder requestJson = new StringBuilder();

    @Override
    public URL getQueryURL() {
        return queryUrl;
    }
    @Override
    public void setQueryURL(URL queryURL) {
        this.queryUrl=queryURL;
    }
    private boolean doLogin() throws IOException {
        try (InputStream inputStream = new FileInputStream("config.properties")) {
            //load current properties
            Properties properties = new Properties();
            properties.load(inputStream);
            String email = properties.getProperty("sc.username");
            email = email.replace("@", "%40");
            String password = properties.getProperty("sc.password");
            String loginPostDataEnc = "username=" + email + "&password=" + password;
            URL url = new URL(BASE_URL + "login/");

            String response = sendQueryRequest(url, loginPostDataEnc);
            if (response.trim().isEmpty() || response.contains("wrong user name"))
                return false;
            else {
                LoginJsonResponse loginJsonResponse = mapLoginJsonResponse(response);
                if (loginJsonResponse.result.equals("success")) {
                    OutputStream outputStream = new FileOutputStream("config.properties");
                    properties.setProperty("sc.date", Long.toString(System.currentTimeMillis()));
                    properties.setProperty("sc.token", loginJsonResponse.token);
                    properties.setProperty("sc.user", Integer.toString(loginJsonResponse.user));
                    properties.store(outputStream,null);
                    return true;
                }
                return false;
            }
        }
    }
    private String[] getUserToken(boolean forcedUpdate) throws IOException {
        //first check if 6 months has passed since last doLogin. if yes then doLogin, else no need to doLogin.
        try (InputStream inputStream = new FileInputStream("config.properties")) {
            //load current properties
            Properties properties = new Properties();
            properties.load(inputStream);

            String[] userToken = {"",""};

/*            if (forcedUpdate) {
                if (!doLogin()) {
                    System.out.println("problem logging in!");
                    return userToken;
                }
            }*/
            properties.load(inputStream);
            userToken[0] = properties.getProperty("sc.user");
            userToken[1] = properties.getProperty("sc.token");

            return userToken;
/*            long sixMonths = 6 * 30 * 24 * 60 * 60;
            sixMonths = sixMonths * 1000; //avoid overflow
            if ((System.currentTimeMillis() - Long.parseLong(properties.getProperty("sc.date")) > sixMonths)) {
                return (doLogin());
            }*/
        }
    }

    private LoginJsonResponse mapLoginJsonResponse(String response) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response, LoginJsonResponse.class);
    }
    private String sendQueryRequest(URL url, String reqeuest) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setConnectTimeout(15000);
        urlConnection.setReadTimeout(15000);
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Accept-Encoding", "gzip");
        urlConnection.setRequestProperty("Accept-Language", "en-us,en;q=0.5");
        urlConnection.setRequestProperty("Pragma", "no-cache");
        urlConnection.setRequestProperty("Cache-Control", "no-cache");
        urlConnection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Kodi/18.1 Chrome/59.7.3071.114 Safari/537.36");

        try {
            OutputStream output = urlConnection.getOutputStream();
            output.write(reqeuest.getBytes("UTF-8"));
            InputStream inputStream = urlConnection.getInputStream();
            if (urlConnection.getContentEncoding().equals("gzip"))
                inputStream = new GZIPInputStream(inputStream);
            if (urlConnection.getResponseCode() != 200)
                return "";
            else {
                String response = "";
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) { //try with resources, so they will be closed when we are done.
                    char[] readBuffer = new char[2048];
                    int responseSize = bufferedReader.read(readBuffer); //let's read and see the response size
                    while (responseSize > 0) {
                        response = response + String.copyValueOf(readBuffer, 0, responseSize); //must specify the offset and count to read, else will end up with more garbage at the end of the read buffer
                        responseSize = bufferedReader.read(readBuffer);
                    }
                }
                return response;
            }
        } catch (java.net.SocketTimeoutException e) {
            Logger.logException(e, "timeout for request in SubsCenter");
            return "";
        }
    }
    @Override
    public void generateQueryURL(MediaFile mediaFile) throws MalformedURLException {
        setQueryURL(new URL(BASE_URL+"search/?"));
    }
    @Override
    public String getQueryJsonResponse(URL url) {
        return null;
    }
    private String constructQueryString(MediaFile mediaFile) throws IOException {
        //FIX: handle bad or outdated login
        StringBuilder query = new StringBuilder("q=");
        String title = mediaFile.getTitle();
        Pattern pattern = Pattern.compile("19\\d\\d|20\\d\\d"); //subscenter can't handle year in tv title
        Matcher matcher = pattern.matcher(title);
        if (matcher.find())
            query.append(matcher.replaceAll("").trim().replaceAll(" ","+"));
        else
            query.append(title.replaceAll(" ","+"));
        if(!mediaFile.getEpisode().equals("0")) { //it's a tv show
            query.append("&type=series&season=").append(mediaFile.getSeason()).append("&episode=").append(
                    mediaFile.getEpisode());
        }
        else {
            if (mediaFile.getYear()==null)
                query.append("&type=movies").append("&year_start=0").append("&year_end=0");
            else
                query.append("&type=movies").append("&year_start=").append(
                        String.valueOf(Integer.parseInt(mediaFile.getYear()) - 1)).append(
                        "&year_end=").append(mediaFile.getYear());
        }
        query.append("&user=").append(getUserToken(false)[0]).append("&token=").append(
                getUserToken(false)[1]);
        return query.toString();
    }
    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        String[] titleRatingResponse = {"0", "0"};
        String query = constructQueryString(mediaFile);
        generateQueryURL(mediaFile);
        String response = sendQueryRequest(getQueryURL(), query);
        if (response.equals(""))
            return titleRatingResponse;
        QueryJsonResponse queryJsonResponse = mapQueryJsonResponse(response);

        //handle bad token
        if (queryJsonResponse.result.equals("failed"))
            if (queryJsonResponse.message.equals("token not valid")) {
                doLogin();
                query = constructQueryString(mediaFile);
                response = sendQueryRequest(getQueryURL(), query);
                queryJsonResponse = mapQueryJsonResponse(response);
            }
        // handle bad token for the second time, or empty response
        if (queryJsonResponse.result.equals("failed") || queryJsonResponse.data.size() == 0) {
            return titleRatingResponse;
        }
        int maxRating = 0;
        String highestRatingLink ="";
        if (queryJsonResponse.data.size()>1)
            return titleRatingResponse;
        //for (QueryJsonResponse.Datum subData:queryJsonResponse.data)
        if (queryJsonResponse.data.get(0).subtitles.he != null) {
            for (QueryJsonResponse.Datum.Subtitles.He subItem : queryJsonResponse.data.get(0).subtitles.he) {
                String testedTitle = subItem.version.toLowerCase();
                String[] testedTitleWordArray = testedTitle.replaceAll("_", " ").replaceAll
                        ("\\.", " ").replaceAll("-", " ").split(" ");
                int rating = 0;
                for (String word : titleWordsArray) {
                    if (Arrays.asList(testedTitleWordArray).contains(word))
                        rating++;
                }
                if (rating > maxRating) {
                    maxRating = rating;
                    highestRatingLink = subItem.version + ',' + subItem.key + ',' + subItem.id;
                }
            }
        }
        titleRatingResponse[0] = highestRatingLink;
        titleRatingResponse[1] = String.valueOf(maxRating);
        return titleRatingResponse;
    }
    private QueryJsonResponse mapQueryJsonResponse (String response) throws IOException {
        //ObjectMapper objectMapper = new ObjectMapper();
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response, QueryJsonResponse.class);
    }

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        String[] subID = subId.split(",");
        String downloadUrlString = BASE_URL + "subtitle/download/he/?" + "sub_id=" + subID[2] + "&key=" +
                subID[1] + "&v=" + subID[0];
        String request = "user=" + getUserToken(false)[0] + "&token=" +
                getUserToken(false)[1];
        URL downloadUrl = new URL(downloadUrlString);
        return (sendDownloadRequest(downloadUrl, request,mediaFile));

    }
    private boolean sendDownloadRequest(URL url, String request, MediaFile mediaFile) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Accept-Encoding", "gzip");
        urlConnection.setRequestProperty("Accept-Language", "en-us,en;q=0.5");
        urlConnection.setRequestProperty("Pragma", "no-cache");
        urlConnection.setRequestProperty("Cache-Control", "no-cache");
        urlConnection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Kodi/18.1 Chrome/59.7.3071.114 Safari/537.36");

        try (OutputStream output = urlConnection.getOutputStream()) {
            output.write(request.getBytes(StandardCharsets.UTF_8));
        }
        InputStream inputStream = urlConnection.getInputStream();
        if (urlConnection.getResponseCode() != 200)
            return false;
        else {
            File subZip = new File(FilenameUtils.removeExtension(mediaFile.getPathName() + "\\" +
                    mediaFile.getFileName()) + ".zip");

            try (ReadableByteChannel rbc = Channels.newChannel(inputStream); //try with resources
                 FileOutputStream fos = new FileOutputStream(subZip)) {
                long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
                if (bytesTransferred == 0) {
                    return false;
                }
                else {
                    extractSubFromZip(mediaFile,subZip);
                }
                fos.close();
                if (!subZip.delete())
                    return false;
            }
        }
        return true;
    }
    private void extractSubFromZip(MediaFile mediaFile, File subZip) throws IOException {
        StringBuilder subFileInZip = new StringBuilder();
        final String[] allowedSubExtensions = {"srt", "sub"};
        List<FileHeader> fileHeaders = new ZipFile(subZip).getFileHeaders();
        for (FileHeader fileHeader : fileHeaders) {
            if (FilenameUtils.isExtension(fileHeader.getFileName(), allowedSubExtensions)) {
                subFileInZip.append(fileHeader.getFileName());
                break;
            }
        }
        new ZipFile(subZip).extractFile(subFileInZip.toString(), mediaFile.getPathName());
        File extractedSubFile = new File(mediaFile.getPathName() + "\\" + subFileInZip);
        File newSubFile = new File(mediaFile.getPathName() + "\\" +
                FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + PropertiesClass.getLangSuffix()+'.' +
                FilenameUtils.getExtension(extractedSubFile.toString()));
        if (!extractedSubFile.renameTo(newSubFile))
            Logger.logger.warning("could not rename the file!");
    }

    static class LoginJsonResponse {
        public String token;
        public int user;
        public String result;
    }
    static class QueryJsonResponse {
        public List<Datum> data;
        public String result;
        public String message;

        static class Datum {
            public Subtitles subtitles;

            static class Subtitles {
                public List<He> he;

                static class He {
                    public String version;
                    public String id;
                    public String key;
                }
            }
        }
    }
}
