package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.MediaFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public interface ISubProvider {
    URL getQueryURL();
    void setQueryURL(URL queryURL);

    void generateQueryURL(MediaFile mediaFile) throws MalformedURLException;
    String getQueryJsonResponse(URL url) throws IOException;
    boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException;
//    void getSub (MediaFile mediaFile) throws IOException;
    String[] getRating (MediaFile mediaFile, String[] titleWordsArray) throws IOException;
}
