package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wtekiela.opensub4j.api.OpenSubtitlesClient;
import com.github.wtekiela.opensub4j.impl.OpenSubtitlesClientImpl;
import com.github.wtekiela.opensub4j.response.*;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.xmlrpc.XmlRpcException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

public class OpensubtitlesSubProvider implements ISubProvider {
    private URL queryURL;
    private String language;
    // alternate login is the username (email)/password login, which is different to the regular login because it
    // doesn't allow searching by imdbId together with season/series, instead relying on title+seasons+episode.
    boolean alternativeLogin = true;
    OpenSubtitlesClient osClient;
    String chosenSubName;
    String chosenSubFormat;

    @Override
    public String getChosenSubName() {
        return chosenSubName;
    }

    OpensubtitlesSubProvider() {
        this.alternativeLogin = (!PropertiesClass.getOpenSubtitlesUsername().trim().isEmpty() &&
                !PropertiesClass.getOpenSubtitlesPassword().trim().isEmpty());
        if (alternativeLogin) {
            Logger.logger.fine("using OpenSubtitles username (email)/password login.");
            this.osClient = doAlternativeLogin();
        }
        else
            Logger.logger.fine("using OpenSubtitles regular login.");
    }

    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        if (language.length() != 2) {
            Logger.logger.warning(String.format("expected 2 letter language code, but got %d letter language (code): %s," +
                            "will default to Hebrew.", language.length(), language));
            this.language = "heb";
        }
        else {
            Locale locale = new Locale(language);
            try {
                this.language = locale.getISO3Language();
            } catch (java.util.MissingResourceException exception) {
                Logger.logger.warning(String.format("invalid language code provided (%s), will default to Hebrew.", language));
                this.language = "heb";
            }
        }
    }

    @Override
    public URL getQueryURL() {
        return queryURL;
    }
    @Override
    public void setQueryURL(URL queryURL) {
        this.queryURL = queryURL;
    }

    @Override
    public void generateQueryURL(MediaFile mediaFile) throws MalformedURLException {
        setLanguage(PropertiesClass.getLangSuffix().replace(".",""));
        StringBuilder url = new StringBuilder("https://rest.opensubtitles.org/search/"+
                "sublanguageid-"+getLanguage()+"/");
        if (!mediaFile.getImdbId().trim().isEmpty())
            url.append("imdbid-").append(mediaFile.getImdbId(), 2, 9);
        else
            url.append("tag-").append(mediaFile.getFileName().toLowerCase());
        if(!mediaFile.getEpisode().equals("0")) {
            DecimalFormat formatter = new DecimalFormat("0");
            String formattedSeason = formatter.format(Integer.parseInt(mediaFile.getSeason()));
            String formattedEpisode = formatter.format(Integer.parseInt(mediaFile.getEpisode()));
            url.append("/season-").append(formattedSeason).append("/").append("episode-").append(formattedEpisode);
        }
        this.queryURL = new URL(url.toString());
    }

    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("User-Agent", PropertiesClass.getOpenSubtitlesUserAgent());
        InputStream inputStream = urlConnection.getInputStream();
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

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        URLConnection con = (new URL(subId)).openConnection();
        File subZip = new File(FilenameUtils.removeExtension(mediaFile.getPathName()+"\\"+
                mediaFile.getFileName())+ (alternativeLogin ? ".gz" : ".zip"));

        try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); //try with resources
             FileOutputStream fos = new FileOutputStream(subZip)) {
            long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
            if (bytesTransferred == 0)
                return false;
            else {
                // in the alternate login, we have a .gz file, and we first need to get the real extension
                // edit: the API also gives us ZIP download, so we can just use that. keep code to use later if needed
                if (false && alternativeLogin) {
                    // TODO: if planning to use this later, just switch to chosenSubFormat instead of finding it from filename.
                    Logger.logger.finer("searching for the extension in the header for the file download http request.");
                    // assume .srt and hope we can get it from the headers
                    String extension = ".srt";
                    String fieldValue = con.getHeaderField("Content-Disposition");
                    String wantedField = "filename=\"";
                    if (fieldValue != null && fieldValue.contains(wantedField)) {
                        String filename = fieldValue.substring(fieldValue.indexOf(wantedField) + wantedField.length(), fieldValue.length()-1);
                        extension = "."+FilenameUtils.getExtension(FilenameUtils.removeExtension(filename));
                        Logger.logger.finer(String.format("found extension %s.", extension));
                    }
                    String outputSubFileName = mediaFile.getPathName() + "\\" +
                            FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + PropertiesClass.getLangSuffix() + extension;

                    try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(subZip));
                         FileOutputStream fosGz = new FileOutputStream(outputSubFileName)) {
                        // copy GZIPInputStream to FileOutputStream
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = gis.read(buffer)) > 0)
                            fosGz.write(buffer, 0, len);
                    }
                } else {
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
                            FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + PropertiesClass.getLangSuffix() + '.' +
                            FilenameUtils.getExtension(extractedSubFile.toString()));
                    if (!extractedSubFile.renameTo(newSubFile))
                        Logger.logger.warning("could not rename the file");
                    fos.close();
                    //if (!subZip.delete())
                    //    Logger.logger.warning("can't delete the sub zip file!");
                }
            }
        } catch (Exception e) {
            Logger.logException(e, "donwloading subtitle");
            return false;
        }
        if (!subZip.delete())
            Logger.logger.warning("can't delete the sub zip file!");
        return true;
    }

/*    @Override
    public void getSub (MediaFile mediaFile) throws IOException {
        generateQueryURL(mediaFile);
        QueryJsonResponse[] queryJsonResponses = mapJsonResponse(getQueryJsonResponse(getQueryURL()));
        downloadSubFile(getTitleRating(queryJsonResponses,mediaFile.getFileName()),mediaFile);
    }*/

    private OpenSubtitlesClient doAlternativeLogin() {
        try {
            URL serverUrl = new URL("https", "api.opensubtitles.org", 443, "/xml-rpc");
            OpenSubtitlesClient osClient = new OpenSubtitlesClientImpl(serverUrl);
            // logging in
            LoginResponse loginResponse = (LoginResponse) osClient.login("jointdogg@gmail.com", "asdasd", "en", "XBMC_Subtitles_Login_v5.0.16");

            // checking login status
            assert loginResponse.getStatus() == ResponseStatus.OK;
            assert osClient.isLoggedIn();
            return osClient;

        } catch (XmlRpcException | MalformedURLException e) {
            Logger.logException(e, "logging in to OpenSubtitles with the alternate method");
            return null;
        }
    }

    public List<SubtitleInfo> doAlternateSearch(MediaFile mediaFile, OpenSubtitlesClient osClient) {
        setLanguage(PropertiesClass.getLangSuffix().replace(".",""));
        ListResponse<SubtitleInfo> response;
        try {
            // tvshow - search by title, season, episode only
            if (!mediaFile.getEpisode().equals("0")) {
                response = osClient.searchSubtitles(getLanguage(), mediaFile.getTitle(), mediaFile.getSeason(), mediaFile.getEpisode());
            }
            // movie - we can search by imdb or filename
            else if (!mediaFile.getImdbId().trim().isEmpty()) {
                response = osClient.searchSubtitles(getLanguage(), mediaFile.getImdbId().trim().replace("tt",""));
            } else {
                response = osClient.searchSubtitles("eng", new File(mediaFile.getFileName()));
            }
            Optional<List<SubtitleInfo>> subtitles = response.getData();

            if (subtitles.isPresent()) {
                return subtitles.get();
            }
        } catch (XmlRpcException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        if (alternativeLogin && osClient!= null) {
            List<SubtitleInfo> subList = doAlternateSearch(mediaFile, osClient);
            if (subList != null) {
                String[] ratingResponseArray = getTitleRating(subList, titleWordsArray, mediaFile);
                return ratingResponseArray;
            }
            else
                return null;
        }
        else {
            generateQueryURL(mediaFile);
            QueryJsonResponse[] queryJsonResponses = mapJsonResponse(getQueryJsonResponse(getQueryURL()));
            String[] ratingResponseArray = getTitleRating(queryJsonResponses, titleWordsArray);
            return ratingResponseArray;
        }
    }

    private QueryJsonResponse[] mapJsonResponse (String response) throws IOException {
        //ObjectMapper objectMapper = new ObjectMapper();
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response,QueryJsonResponse[].class);
    }

    private String[] getTitleRating(List<SubtitleInfo> subList, String[] titleWordsArray, MediaFile mediaFile) {
        int maxRating = 0;
        String highestRatingLink ="";
        for (SubtitleInfo subInfo : subList) {
            String testedTitle = subInfo.getFileName().toLowerCase().trim();
            String[] testedTitleWordArray = testedTitle.replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            // added bonus for matched series imdb id
            if (!mediaFile.getImdbId().isEmpty() && mediaFile.getImdbId().contains(subInfo.getSeriesImdbId()))
                rating = rating+3;
            // added bonus for non hearing impaired subs if looking for English subs
            if (this.language.equals("eng") && testedTitle.contains("nonhi"))
                rating++;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = subInfo.getZipDownloadLink();
                chosenSubName = subInfo.getFileName();
                chosenSubFormat = subInfo.getFormat();
            }
        }
        String[] titleRatingResponse={highestRatingLink,String.valueOf(maxRating)};
        return titleRatingResponse;
    }

    private String[] getTitleRating(QueryJsonResponse[] queryResponseArray, String[] titleWordsArray) {
        int maxRating = 0;
        String highestRatingLink ="";
        for (QueryJsonResponse queryJsonResponse:queryResponseArray) {
            String testedTitle = queryJsonResponse.MovieReleaseName.toLowerCase().trim();
            String[] testedTitleWordArray = testedTitle.replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = queryJsonResponse.ZipDownloadLink;
                chosenSubName = queryJsonResponse.MovieReleaseName;
            }
        }
        String[] titleRatingResponse={highestRatingLink,String.valueOf(maxRating)};
        return titleRatingResponse;
    }

    static class QueryJsonResponse {
        private String MovieReleaseName;
        private String ZipDownloadLink;

        public String getMovieReleaseName() {
            return MovieReleaseName;
        }
        @JsonProperty("MovieReleaseName")
        public void setMovieReleaseName(String MovieReleaseName) {
            this.MovieReleaseName = MovieReleaseName;
        }
        public String getZipDownloadLink() {
            return ZipDownloadLink;
        }
        @JsonProperty("ZipDownloadLink")
        public void setZipDownloadLink(String ZipDownloadLink) {
            this.ZipDownloadLink = ZipDownloadLink;
        }
    }
}
