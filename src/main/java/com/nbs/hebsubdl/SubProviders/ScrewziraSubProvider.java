package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ScrewziraSubProvider implements ISubProvider {
    private URL queryURL;
    private StringBuilder requestJson = new StringBuilder();

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
        String type;
        if (mediaFile.getEpisode().equals("0")) {
            // movie
            type = "0";
            this.setQueryURL(new URL("http://api.screwzira.com/FindFilm"));
            constructJsonRequest(mediaFile,"film");
        }
        else {
            // tv show
            type = "1";
            this.setQueryURL(new URL("http://api.screwzira.com/FindSeries"));
            constructJsonRequest(mediaFile,"tv");
        }
    }
    private void constructJsonRequest(MediaFile mediaFile, String type) {
        StringBuilder request = new StringBuilder("{\"request\":{\"SearchPhrase\": \""+mediaFile.getImdbId()+
                "\",\"SearchType\": \"ImdbID\",\"Version\":\"1.0\"");
        if (type.equals("tv")) {
            request.append(",\"Season\":" + Integer.parseInt(mediaFile.getSeason()));
            request.append(",\"Episode\":" + Integer.parseInt(mediaFile.getEpisode()));
        }
        request.append("}}");
        this.requestJson.append(request);
    }
    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type","application/json");

        try (OutputStream output = urlConnection.getOutputStream()) {
            output.write(this.requestJson.toString().getBytes("UTF-8"));
        }

        InputStream inputStream = urlConnection.getInputStream();
        String response="";
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));) { //try with resources, so they will be closed when we are done.
            char[] readBuffer = new char[2048];
            int responseSize=bufferedReader.read(readBuffer); //let's read and see the response size
            while (responseSize > 0) {
                response = response+String.copyValueOf(readBuffer,0,responseSize); //must specify the offset and count to read, else will end up with more garbage at the end of the read buffer
                responseSize = bufferedReader.read(readBuffer);
            }
        }
        String cleanedResponse = response.replaceAll("\\\\","");
        return cleanedResponse.substring(1,cleanedResponse.length()-1);
    }
    private QueryJsonResponse mapJsonResponse (String response) throws IOException {
        //ObjectMapper objectMapper = new ObjectMapper();
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response,QueryJsonResponse.class);
    }

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        generateQueryURL(mediaFile);
        QueryJsonResponse queryResponseArray = mapJsonResponse(getQueryJsonResponse(this.getQueryURL()));
        String[] ratingResponseArray={"",""};
        ratingResponseArray[1]=String.valueOf(getTitleRating(queryResponseArray,titleWordsArray));
        ratingResponseArray[0]=queryResponseArray.maxRating;
        return ratingResponseArray;
    }

    private int getTitleRating(QueryJsonResponse QueryResponseArray, String[] titleWordsArray) {
        int maxRating = 0;
        for (QueryJsonResponse.JsonResponseArray response:QueryResponseArray.Results) {
            String testedTitle = response.SubtitleName.toLowerCase();
            String[] testedTitleWordArray = testedTitle.replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                QueryResponseArray.maxRating = response.Identifier;
            }
        }
        return maxRating;
    }

    @Override
    public boolean downloadSubFile(String maxRating, MediaFile mediaFile) throws IOException {
        URL url = new URL("http://api.screwzira.com/Download");
        URLConnection urlConnection = url.openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type","application/json");
        StringBuilder request = new StringBuilder("{\"request\":{\"subtitleID\": \""+maxRating+"\"}}");
        try (OutputStream output = urlConnection.getOutputStream()) {
            output.write(request.toString().getBytes("UTF-8"));
        }
        String constToTrim = "attachment; filename=";
        String fileName = urlConnection.getHeaderField("Content-Disposition");
        fileName = fileName.substring(constToTrim.length(),fileName.length());
        File filePath = new File(mediaFile.getPathName()+"\\"+
                FilenameUtils.removeExtension(mediaFile.getOriginalFileName())+PropertiesClass.getLangSuffix()+'.'+
                FilenameUtils.getExtension(fileName.toString()));
        long bytesTransferred = 0;
        try (ReadableByteChannel rbc = Channels.newChannel(urlConnection.getInputStream()); //try with resources
            FileOutputStream fos = new FileOutputStream(filePath)) {
            bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
        }
        if (bytesTransferred == 0) {
            filePath.delete();
            return false;
        }
        return true;
    }

    static class QueryJsonResponse {
        public JsonResponseArray[] Results;
        public boolean IsSuccess;
        public String ErrorMessage;
        public String maxRating;

        static class JsonResponseArray {
            public String SubtitleName;
            public String Identifier;
        }
    }
}
