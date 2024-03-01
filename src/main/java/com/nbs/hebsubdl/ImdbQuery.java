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
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImdbQuery {
    static void getImdbID(ArrayList<MediaFile> mediaFilesList) {
        for (MediaFile mediaFile : mediaFilesList) {
            if (FindSubs.subAlreadyExists(mediaFile))
                continue;
            String searchQuery = mediaFile.getTitle() + (mediaFile.getYear() == null ? "" : " " + mediaFile.getYear());
            String URL = prepareImdbQueryUrl(searchQuery);
            searchQuery = searchQuery.replaceAll(" ", "_");
            String callback = "imdb$" + searchQuery + "(";
            try {
                ImdbJson response = sendImdbQuery(callback, URL);
                // for the case of no match at all
                if (response.getD() == null) {
                    mediaFile.setImdbId("");
                    continue;
                } else {
                    String currentImdbId = null;
                    int minScore = 100; // small enough
                    for (ImdbJson.ImdbJsonArray item : response.getD()) {
                        boolean isMovie = mediaFile.getSeason() == "0" && mediaFile.getEpisode() == "0";
                        if (item.getQid() == null || (isMovie != item.isMovie))
                            continue;
                        String imdbTitle = item.getL().toLowerCase();
                        int score = StringDistance.calculate(mediaFile.getTitle(), imdbTitle);

                        if (score < minScore) {
                            minScore = score;
                            currentImdbId = item.getId();
                        }
                    }
                    if (currentImdbId == null || currentImdbId.isEmpty())
                        currentImdbId = response.getD()[0].getId();
                    // make sure we are getting correct imdbid for our query. a valid imdb id is
                    // ttXXXXXXX, but I'm not sure
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

    private static ImdbJson sendImdbQuery(String callback, String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        InputStream inputStream = urlConnection.getInputStream();
        String response = "";
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) { // try with resources, so they will be
                                                                               // closed when we are done.
            char[] readBuffer = new char[2048];
            int responseSize = bufferedReader.read(readBuffer); // let's read and see the response size
            while (responseSize > 0) {
                response = response + String.copyValueOf(readBuffer, 0, responseSize); // must specify the offset and
                                                                                       // count to read, else will end
                                                                                       // up with more garbage at the
                                                                                       // end of the read buffer
                responseSize = bufferedReader.read(readBuffer);
            }
        }
        Pattern pattern = Pattern.compile("\\{.*\\}");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find())
            response = matcher.group(0);
        // response=response.substring(callback.length());
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        return objectMapper.readValue(response, ImdbJson.class);
    }

    private static String prepareImdbQueryUrl(String searchQuery) {
        searchQuery = searchQuery.replaceAll(" ", "%20");
        return ("https://v2.sg.media-imdb.com/suggests/" + searchQuery.toCharArray()[0] + "/" + searchQuery + ".json");
    }

    static class StringDistance {

        static int calculate(String x, String y) {
            int[][] dp = new int[x.length() + 1][y.length() + 1];

            for (int i = 0; i <= x.length(); i++) {
                for (int j = 0; j <= y.length(); j++) {
                    if (i == 0) {
                        dp[i][j] = j;
                    } else if (j == 0) {
                        dp[i][j] = i;
                    } else {
                        dp[i][j] = min(dp[i - 1][j - 1]
                                + costOfSubstitution(x.charAt(i - 1), y.charAt(j - 1)),
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1);
                    }
                }
            }

            return dp[x.length()][y.length()];
        }

        static public int costOfSubstitution(char a, char b) {
            return a == b ? 0 : 1;
        }

        static public int min(int... numbers) {
            return Arrays.stream(numbers)
                    .min().orElse(Integer.MAX_VALUE);
        }
    }
}
