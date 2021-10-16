package com.nbs.hebsubdl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaFile {
      private String title;
      private String imdbId;
      private String year;
      private String season="0";
      private String episode="0";
      private String episodes;
      private String pathName;
      private String fileName;
      private String originalFileName;

    //constructor:
    public MediaFile(String item) {
        Path path = Paths.get(item);
        this.fileName = path.getFileName().toString();
        this.pathName = path.getParent().toString();
    }

    //getters and setters:
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getImdbId() {
        return imdbId;
    }
    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }
    public String getYear() {
        return year;
    }
    public void setYear(String year) {
        this.year = year;
    }
    public String getSeason() {
        return season;
    }
    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getOriginalFileName() {
        return originalFileName;
    }
    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
    public void setSeason(String season) {
        this.season = season;
    }
    public String getEpisode() {
        return episode;
    }
    public void setEpisode(String episode) {
        this.episode = episode;
    }
    public String getEpisodes() {
        return episodes;
    }
    public void setEpisodes(String episodes) {
        this.episodes = episodes;
    }
    public String getPathName() {
        return pathName;
    }
    public void setPathName(String pathName) {
        this.pathName = pathName;
    }

    public static void parse (MediaFile mediaFile) {
        mediaFile.setOriginalFileName(mediaFile.fileName);
        String cleaned = clean(mediaFile);

        char[] charArrayForReplacement = cleaned.toCharArray();
        if (cleaned.lastIndexOf('-') > 0 )
            charArrayForReplacement[cleaned.lastIndexOf('-')] = '.';
        String cleanedToSplit = String.valueOf(charArrayForReplacement);

        ArrayList<String> words = new ArrayList<String>(Arrays.asList(cleanedToSplit.toLowerCase().split("\\.")));
        if (words.contains("dd5")) {
            words.set(words.indexOf("dd5"),"dd5.1");
            words.remove(words.indexOf("dd5.1")+1);
        }
        StringBuilder title = new StringBuilder(String.join(" ",words));
        int tvShowIndex = getTvShowIndex(mediaFile,words);
        if (tvShowIndex>0) {
            for (int wordIndex = tvShowIndex - 1; wordIndex >= 0; wordIndex--) {
                if (wordIndex == tvShowIndex - 1)
                    title = new StringBuilder();
                title.insert(0, words.get(wordIndex) + " ");
            }
        }
        else { //it's a movie
            int yearWordIndex = getYear(mediaFile, words);
            //for (int wordIndex = yearWordIndex; wordIndex<=words.size(); wordIndex++) {
            for (int wordIndex = yearWordIndex - 1; wordIndex >= 0; wordIndex--) {
                if (wordIndex == yearWordIndex - 1)
                    title = new StringBuilder();
                //words.remove(yearWordIndex+1);
                title.insert(0, words.get(wordIndex) + " ");
            }
        }
        mediaFile.setTitle(title.toString().trim());
    }
    private static String clean (MediaFile mediaFile) {
        //clean file name from garbage that blocks identification.
        String result = mediaFile.fileName;
        result = result.replaceAll("\\[.*\\]", "");
        result = result.trim();
        result = result.replaceAll("[\\[\\]\\(\\)\\;\\:\\!\\s\\\\\\.]", ".");
        result = result.replaceAll("\\.{2,}", ".");
        result = result.replaceAll("\\.(avi|mp4|mkv)", "");
        mediaFile.setFileName(result);
        return result;
    }
    private static int getYear(MediaFile mediaFile, ArrayList<String> words) {
        Pattern pattern = Pattern.compile("19\\d\\d|20\\d\\d");
        for (int wordIndex=words.size()-1; wordIndex>=0; wordIndex--) {
        //for (String word:words) {
            String word = words.get(wordIndex);
            Matcher matcher = pattern.matcher(word);
            if (matcher.find()) {
                mediaFile.setYear(matcher.group());
                return (words.indexOf(matcher.group()));
            }
        }
        return 0;
    }
    private static int getTvShowIndex(MediaFile mediaFile, ArrayList<String> words) {
        //get the index in the filename-words array of S..E.., and set episode/season info for file
        for (String word : words) {
            Pattern pattern = Pattern.compile("s\\d\\de\\d\\d.*");
            Matcher matcher = pattern.matcher(word);
            if (matcher.find()) {
                String[] tvInfo = matcher.group().split("e");
                mediaFile.setSeason(tvInfo[0].substring(1, 3));
                mediaFile.setEpisode(tvInfo[1].substring(0, 2));
                return words.indexOf(matcher.group());
            }
            else {
                //this might be a mini series with only episode naming
                pattern = Pattern.compile("e\\d\\d");
                matcher = pattern.matcher(word);
                if (matcher.find()) {
                    String[] tvInfo = matcher.group().split("e");
                    mediaFile.setSeason("01");
                    mediaFile.setEpisode(tvInfo[1].substring(0, 2));
                    return words.indexOf(matcher.group());
                }
            }
        }
        return 0;
    }
}
