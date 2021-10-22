package com.nbs.hebsubdl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.SubProviders.FindSubs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImdbQuery {
    static void getImdbID (ArrayList<MediaFile> mediaFilesList) {
        for (MediaFile mediaFile:mediaFilesList) {
            if (FindSubs.subAlreadyExists(mediaFile))
                continue;
            String searchQuery = mediaFile.getTitle()+(mediaFile.getYear() == null ? "" : " "+mediaFile.getYear());
            String URL = prepareImdbQueryUrl(searchQuery);
            searchQuery=searchQuery.replaceAll(" ","_");
            String callback = "imdb$"+searchQuery+"(";
            try {
                ImdbJson response = sendImdbQuery(callback,URL);
                //for the case of no match at all
                if (response.getD() == null) {
                    mediaFile.setImdbId("");
                    continue;
                } else {
                    String currentImdbId = null;
                    for (ImdbJson.ImdbJsonArray item : response.getD()) {
                        int score = 0;
                        String[] imdbTitle = item.getL().toLowerCase().replace(":","").split(" ");
                        for (String word : imdbTitle) {
                            if (mediaFile.getTitle().contains(word))
                                score++;
                            else
                                score--;
                        }
                        if (score == mediaFile.getTitle().split(" ").length) {
                            currentImdbId = item.getId();
                            break;
                        }
                    }
                    if (currentImdbId == null || currentImdbId.isEmpty())
                        currentImdbId = response.getD()[0].getId();
                    // make sure we are getting correct imdbid for our query. a valid imdb id is ttXXXXXXX, but I'm not sure
                    // how many integers it will be in the future.
                    Pattern pattern = Pattern.compile("tt\\d\\d\\d\\d.*");
                    Matcher matcher = pattern.matcher(currentImdbId);
                    if (matcher.find())
                        mediaFile.setImdbId(currentImdbId);
                    else
                        mediaFile.setImdbId("");
                }
            } catch (IOException e) {
                Logger.logException(e, "sending IMDB query, or getting IMDB ID from response.");
            }


        }
    }

    private static ImdbJson sendImdbQuery (String callback, String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        InputStream inputStream = urlConnection.getInputStream();
        String response="";
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) { //try with resources, so they will be closed when we are done.
            char[] readBuffer = new char[2048];
            int responseSize=bufferedReader.read(readBuffer); //let's read and see the response size
            while (responseSize > 0) {
                response = response+String.copyValueOf(readBuffer,0,responseSize); //must specify the offset and count to read, else will end up with more garbage at the end of the read buffer
                responseSize = bufferedReader.read(readBuffer);
            }
        }
        response=response.substring(callback.length());
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response,ImdbJson.class);
    }

    private static String prepareImdbQueryUrl(String searchQuery) {
        searchQuery=searchQuery.replaceAll(" ","%20");
        return("https://v2.sg.media-imdb.com/suggests/"+searchQuery.toCharArray()[0]+"/"+searchQuery+".json");
    }
}
