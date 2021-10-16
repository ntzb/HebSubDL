package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class WizdomSubProvider implements ISubProvider {
    private URL queryURL;

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
        this.setQueryURL(new URL("http://json.wizdom.xyz/search.php?action=by_id&imdb="+mediaFile.getImdbId()+
                "&season="+mediaFile.getSeason()+"&episode="+mediaFile.getEpisode()+
                "&version="+mediaFile.getFileName()));
    }
    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        try {
            URLConnection urlConnection = url.openConnection();
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
            return (response.equals("[]") ? null : response);
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }
    private QueryJsonResponse[] mapJsonResponse (String response) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response,QueryJsonResponse[].class);
    }

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        File subZip = new File(FilenameUtils.removeExtension(mediaFile.getPathName()+"\\"+
                mediaFile.getFileName())+".zip");
        URL url = new URL("http://zip.wizdom.xyz/"+subId+".zip");
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream()); //try with resources
             FileOutputStream fos = new FileOutputStream(subZip)) {
            long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
            if (bytesTransferred == 0)
                return false;
            else {
                StringBuilder subFileInZip = new StringBuilder();
                final String[] allowedSubExtensions = {"srt", "sub"};
                List<FileHeader> fileHeaders = new ZipFile(subZip).getFileHeaders();
                for (FileHeader fileHeader: fileHeaders) {
                    if (FilenameUtils.isExtension(fileHeader.getFileName(),allowedSubExtensions)) {
                        subFileInZip.append(fileHeader.getFileName());
                        break;
                    }
                }
                new ZipFile(subZip).extractFile(subFileInZip.toString(), mediaFile.getPathName());
                File extractedSubFile = new File(mediaFile.getPathName()+"\\"+subFileInZip);
                File newSubFile = new File(mediaFile.getPathName()+"\\"+
                        FilenameUtils.removeExtension(mediaFile.getOriginalFileName())+'.'+ PropertiesClass.getLangSuffix()+'.'+
                        FilenameUtils.getExtension(extractedSubFile.toString()));
                if (!extractedSubFile.renameTo(newSubFile))
                    System.out.println("could not rename the file!");
                fos.close();
                if (!subZip.delete())
                    System.out.println("can't delete the sub zip file!");
            }
        }
        return true;
    }
    static class QueryJsonResponse {
        public String versioname;
        public String id;

    }

    private int getTitleRating(String[] titleWordArray, String matchedTitle) {
        String[] testedTitleWordArray = matchedTitle.toLowerCase().replaceAll("_", " ").replaceAll
                ("\\.", " ").replaceAll("-", " ").split(" ");
        int rating = 0;
        for (String word : titleWordArray) {
            if (Arrays.asList(testedTitleWordArray).contains(word))
                rating++;
        }
        return rating;
    }

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        //wizdom only works with imdb. if a file doesn't have one, we'll not even search..
        String[] ratingResponseArray={"0","0"};
        if(mediaFile.getImdbId().equals(""))
            return ratingResponseArray;
        generateQueryURL(mediaFile);
        String response = getQueryJsonResponse(getQueryURL());
        if (response == null || response.trim().isEmpty())
            return ratingResponseArray;
        QueryJsonResponse newResponse = mapJsonResponse(response)[0];
        ratingResponseArray[0]=newResponse.id;
        ratingResponseArray[1]=String.valueOf(getTitleRating(titleWordsArray,newResponse.versioname));
        return ratingResponseArray;
    }
}
