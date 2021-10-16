package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.MainGUI;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class FindSubs {
    public static void findSubs(ArrayList<MediaFile> mediaFileList, DefaultTableModel model, JTable jTable) throws IOException {
        List<ISubProvider> providersList = new ArrayList<>(); //create a list of all providers to make iteration easy
        //WizdomSubProvider wizdomSubProvider = new WizdomSubProvider();
        //KtuvitSubProvider ktuvitSubProvider = new KtuvitSubProvider();
        //OpensubtitlesSubProvider opensubtitlesSubProvider = new OpensubtitlesSubProvider();
        //SubscenterSubProvider subscenterSubProvider = new SubscenterSubProvider();

        //providersList.add(new WizdomSubProvider());
        providersList.add(new KtuvitSubProvider());
        //providersList.add(new OpensubtitlesSubProvider());
        //providersList.add(subscenterSubProvider); - DEAD
        int count = 1;
        for (MediaFile mediaFile : mediaFileList) {
            if (subAlreadyExists(mediaFile)) {
                model.setValueAt("sub already exists", count, 1);
                count++;
                MainGUI.fitColumns(jTable);
                continue;
            }
            String[] titleWordsArray = mediaFile.getFileName().toLowerCase().replaceAll("_", " ").replaceAll
                    ("\\.", " ").replaceAll("-", " ").split(" ");
            int maxTitleRating = titleWordsArray.length, maxRating = 0;
            boolean didDownload = false;
            //ISubProvider bestProvider = wizdomSubProvider;

            //TODO: fix the duplicate providers lists, can handle with only one.
            class SubProviderScore {
                ISubProvider subProvider;
                Integer score;
                String id;

                public SubProviderScore(ISubProvider subProvider, Integer score, String id) {
                    this.subProvider = subProvider;
                    this.score = score;
                    this.id = id;
                }

                public ISubProvider getSubProvider() {
                    return subProvider;
                }
                public Integer getScore() {
                    return score;
                }
            }
            class DescendingScoreComparator implements Comparator <SubProviderScore> {
                @Override
                public int compare(SubProviderScore o1, SubProviderScore o2) {
                    return o2.getScore().compareTo(o1.getScore());
                }
            }
            LinkedList<SubProviderScore> subProviderList = new LinkedList<SubProviderScore>();

            String[] highestRatingSub = {"", ""};
            for (ISubProvider subProvider : providersList) {
                //iterate over list of providers and get the highest rating
                String[] currentRatingSub = subProvider.getRating(mediaFile, titleWordsArray);
                if (Integer.parseInt(currentRatingSub[1]) == maxTitleRating) {
                    //full match, let's finish up
                    model.setValueAt("downloading..", count, 1);
                    didDownload = subProvider.downloadSubFile(currentRatingSub[0], mediaFile);
                    if (didDownload) {
                        model.setValueAt("success!", count, 1);
                        count++;
                        MainGUI.fitColumns(jTable);
                        break;
                    }
                } else {
                    // no full match, get the score
                    SubProviderScore subProviderScore = new SubProviderScore(subProvider,Integer.parseInt(currentRatingSub[1]),currentRatingSub[0]);
                    subProviderList.add(subProviderScore);
/*                    if (Integer.parseInt(currentRatingSub[1]) > maxRating) {
                        maxRating = Integer.parseInt(currentRatingSub[1]);
                        bestProvider = subProvider;
                        highestRatingSub = currentRatingSub;
                    }*/
                }
            }
            Collections.sort(subProviderList,new DescendingScoreComparator());
            if (!didDownload) {
                //if (!highestRatingSub[0].trim().isEmpty()) {
                if (!subProviderList.isEmpty() && subProviderList.get(0).score>0) {
                    //no direct match - let's go with closest one
                    //bestProvider.downloadSubFile(highestRatingSub[0], mediaFile);
                    for(SubProviderScore subProviderScore : subProviderList) {
                        if (subProviderScore.subProvider.downloadSubFile(subProviderScore.id,mediaFile)) {
                            model.setValueAt("success!", count, 1);
                            MainGUI.fitColumns(jTable);
                            count++;
                            didDownload = true;
                            break;
                        }
                        else {
                            model.setValueAt("failed provider, trying next one..", count, 1);
                            MainGUI.fitColumns(jTable);
                        }
                    }
                    if (!didDownload) {
                        model.setValueAt("failed all providers, something wrong?", count, 1);
                        MainGUI.fitColumns(jTable);
                    }
                } else {
                    // no match at all
                    model.setValueAt("failed - didn't find a match", count, 1);
                    MainGUI.fitColumns(jTable);
                    count++;
                }
            }
        }
    }

    public static boolean subAlreadyExists(MediaFile mediaFile) {
        final String[] allowedSubExtensions = {"srt", "sub"};
        for (String extension : allowedSubExtensions) {
            String subFile = mediaFile.getPathName() + "\\" +
                    FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + '.'+ PropertiesClass.getLangSuffix()+'.' + extension;
            File newSubFile = new File(subFile);
            if (newSubFile.exists())
                return true;
        }
        return false;
    }
}
