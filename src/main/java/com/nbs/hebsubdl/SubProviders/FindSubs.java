package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MainGUI;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FindSubs {
    public static void findSubs(ArrayList<MediaFile> mediaFileList, DefaultTableModel model, JTable jTable) throws IOException {
        Logger.logger.info("will search subtitles for " + mediaFileList.size() + " items.");
        List<ISubProvider> providersList = new ArrayList<>(); //create a list of all providers to make iteration easy

        providersList.add(new WizdomSubProvider());
        providersList.add(new KtuvitSubProvider());
        providersList.add(new OpensubtitlesSubProvider());
        //providersList.add(subscenterSubProvider); - DEAD
        int count = 1;
        for (MediaFile mediaFile : mediaFileList) {
            Logger.logger.info("searching subtitles for item: " + mediaFile.getFileName());
            if (subAlreadyExists(mediaFile)) {
                Logger.logger.info("subtitle already exists!" + mediaFile.getFileName());
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
            LinkedList<SubProviderScore> subProviderList = new LinkedList<>();

            //String[] highestRatingSub = {"", ""};
            for (ISubProvider subProvider : providersList) {
                String providerFullClassName = subProvider.getClass().toString().replace("SubProvider","");
                String provider = providerFullClassName.substring(providerFullClassName.lastIndexOf('.') + 1);
                Logger.logger.fine("searching provider: " + provider);

                //iterate over list of providers and get the highest rating
                String[] currentRatingSub = subProvider.getRating(mediaFile, titleWordsArray);
                if (Integer.parseInt(currentRatingSub[1]) == maxTitleRating) {
                    //full match, let's finish up
                    model.setValueAt("downloading..", count, 1);
                    Logger.logger.info(String.format("downloading sub from %s (%s)",provider, subProvider.getChosenSubName()));
                    didDownload = subProvider.downloadSubFile(currentRatingSub[0], mediaFile);
                    if (didDownload) {
                        Logger.logger.info("sub downloaded!");
                        model.setValueAt("success!", count, 1);
                        count++;
                        MainGUI.fitColumns(jTable);
                        break;
                    }
                } else {
                    // no full match, get the score
                    SubProviderScore subProviderScore = new SubProviderScore(subProvider,Integer.parseInt(currentRatingSub[1]),currentRatingSub[0]);
                    subProviderList.add(subProviderScore);
                    Logger.logger.fine("score for the subtitle from provider " + provider + " is " + subProviderScore.score);
                    /*if (Integer.parseInt(currentRatingSub[1]) > maxRating) {
                        maxRating = Integer.parseInt(currentRatingSub[1]);
                        bestProvider = subProvider;
                        highestRatingSub = currentRatingSub;
                    }*/
                }
            }
            if (!didDownload && !subProviderList.isEmpty()) {
                Logger.logger.fine("sorting sub options by score.");
                subProviderList.sort(new DescendingScoreComparator());
                //if (!highestRatingSub[0].trim().isEmpty()) {
                if (subProviderList.get(0).score>0) {
                    //no direct match - let's go with closest one
                    //bestProvider.downloadSubFile(highestRatingSub[0], mediaFile);

                    // try downloading from the first provider, if it fails, try the second one... etc.
                    for(SubProviderScore subProviderScore : subProviderList) {
                        String providerFullClassName = subProviderScore.subProvider.getClass().toString().replace("SubProvider","");
                        String provider = providerFullClassName.substring(providerFullClassName.lastIndexOf('.') + 1);

                        if (subProviderScore.subProvider.downloadSubFile(subProviderScore.id,mediaFile)) {
                            Logger.logger.info(String.format("downloaded sub from %s! (%s)",provider, subProviderScore.subProvider.getChosenSubName()));
                            model.setValueAt("success!", count, 1);
                            MainGUI.fitColumns(jTable);
                            count++;
                            didDownload = true;
                            break;
                        }
                        else {
                            Logger.logger.warning("provider " + provider + "failed, trying the next one.");
                            model.setValueAt("failed provider, trying next one..", count, 1);
                            MainGUI.fitColumns(jTable);
                        }
                    }
                    if (!didDownload) {
                        Logger.logger.warning("all providers failed, something wrong?");
                        model.setValueAt("all providers failed, something wrong?", count, 1);
                        MainGUI.fitColumns(jTable);
                    }
                } else {
                    // no match at all
                    Logger.logger.warning("failed - didn't find a matching sub.");
                    model.setValueAt("failed - didn't find a match.", count, 1);
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
                    FilenameUtils.removeExtension(mediaFile.getOriginalFileName()) + PropertiesClass.getLangSuffix()+'.' + extension;
            File newSubFile = new File(subFile);
            if (newSubFile.exists())
                return true;
        }
        return false;
    }
}
