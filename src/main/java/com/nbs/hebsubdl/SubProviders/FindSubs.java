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
    static List<ISubProvider> providersList = new ArrayList<>(); // create a list of all providers to make iteration
                                                                 // easy

    public static void initProviders() {
        Logger.logger.finer("initializing providers");
        providersList.add(new WizdomSubProvider());
        providersList.add(new KtuvitSubProvider());
        providersList.add(new OpensubtitlesNewSubProvider());

        providersList.forEach(provider -> {
            Logger.logger.info(String.format("provider %s added", getProviderName(provider)));
        });
    }

    public static void findSubs(ArrayList<MediaFile> mediaFileList, DefaultTableModel model, JTable jTable) {
        Logger.logger.info("will search subtitles for " + mediaFileList.size() + " items.");

        int count = 1;
        for (MediaFile mediaFile : mediaFileList) {
            try {
                Logger.logger.info("searching subtitles for item: " + mediaFile.getFileName());
                if (subAlreadyExists(mediaFile)) {
                    Logger.logger.info("subtitle already exists! " + mediaFile.getFileName());
                    model.setValueAt("sub already exists", count, 1);
                    count++;
                    MainGUI.fitColumns(jTable);
                    continue;
                }
                // fix title words array
                String[] titleWordsArray = mediaFile.getFileName().toLowerCase()
                        .replaceAll("dd.{0,2}(2.{0,2}(0|1))", "dd20")
                        .replaceAll("dd.{0,2}(5.{0,2}(0|1))", "dd50")
                        .replace("web-dl", "webdl")
                        .replace("h.264", "h264")
                        .replaceAll("_", " ")
                        .replaceAll("\\.", " ").replaceAll("-", " ").split(" ");
                int maxTitleRating = titleWordsArray.length, maxRating = 0;
                boolean didDownload = false;
                // ISubProvider bestProvider = wizdomSubProvider;

                // TODO: fix the duplicate providers lists, can handle with only one.
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

                class DescendingScoreComparator implements Comparator<SubProviderScore> {
                    @Override
                    public int compare(SubProviderScore o1, SubProviderScore o2) {
                        return o2.getScore().compareTo(o1.getScore());
                    }
                }

                LinkedList<SubProviderScore> subProviderList = new LinkedList<>();
                // String[] highestRatingSub = {"", ""};
                for (ISubProvider subProvider : providersList) {
                    Logger.logger.finer("getting provider name");
                    String provider = getProviderName(subProvider);
                    Logger.logger.fine("searching provider: " + provider);

                    if (!PropertiesClass.getLangSuffix().equals(".he") && subProvider.isHebrewOnly) {
                        Logger.logger.fine("provider " + provider + " skipped since it's hebrew only");
                        // if it's not hebrew, and the provider is hebrew only, skip the provider
                        continue;
                    }

                    // iterate over list of providers and get the highest rating
                    String[] currentRatingSub = subProvider.getRating(mediaFile, titleWordsArray);
                    if (Integer.parseInt(currentRatingSub[1]) == maxTitleRating) {
                        // full match, let's finish up
                        model.setValueAt("downloading..", count, 1);
                        Logger.logger.info(String.format("downloading sub from %s (%s)", provider,
                                subProvider.getChosenSubName()));
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
                        SubProviderScore subProviderScore = new SubProviderScore(subProvider,
                                Integer.parseInt(currentRatingSub[1]), currentRatingSub[0]);
                        subProviderList.add(subProviderScore);
                        Logger.logger.fine(
                                "score for the subtitle from provider " + provider + " is " + subProviderScore.score);
                        /*
                         * if (Integer.parseInt(currentRatingSub[1]) > maxRating) {
                         * maxRating = Integer.parseInt(currentRatingSub[1]);
                         * bestProvider = subProvider;
                         * highestRatingSub = currentRatingSub;
                         * }
                         */
                    }
                }
                if (!didDownload && !subProviderList.isEmpty()) {
                    Logger.logger.fine("sorting sub options by score.");
                    subProviderList.sort(new DescendingScoreComparator());
                    // if (!highestRatingSub[0].trim().isEmpty()) {
                    if (subProviderList.get(0).score > 0) {
                        // no direct match - let's go with closest one
                        // bestProvider.downloadSubFile(highestRatingSub[0], mediaFile);

                        // try downloading from the first provider, if it fails, try the second one...
                        // etc.
                        for (SubProviderScore subProviderScore : subProviderList) {
                            String provider = getProviderName(subProviderScore.subProvider);

                            if (subProviderScore.subProvider.downloadSubFile(subProviderScore.id, mediaFile)) {
                                Logger.logger.info(String.format("downloaded sub from %s! (%s)", provider,
                                        subProviderScore.subProvider.getChosenSubName()));
                                model.setValueAt("success!", count, 1);
                                MainGUI.fitColumns(jTable);
                                count++;
                                didDownload = true;
                                break;
                            } else {
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
                } else if (!didDownload) {
                    // no match at all
                    Logger.logger.warning("failed - no providers available.");
                    model.setValueAt("failed - no providers available.", count, 1);
                    MainGUI.fitColumns(jTable);
                    count++;
                }
            } catch (Exception e) {
                model.setValueAt("failed - error during search.", count, 1);
                MainGUI.fitColumns(jTable);
                count++;
                Logger.logException(e, "error during search");
            }
        }
    }

    public static boolean subAlreadyExists(MediaFile mediaFile) {
        final String[] allowedSubExtensions = { "srt", "sub" };
        for (String extension : allowedSubExtensions) {
            String subFile = String.format("%s/%s%s.%s", mediaFile.getPathName(),
                    FilenameUtils.removeExtension(mediaFile.getOriginalFileName()),
                    PropertiesClass.getLangSuffix(), extension);
            File newSubFile = new File(subFile);
            if (newSubFile.exists())
                return true;
        }
        return false;
    }

    private static String getProviderName(ISubProvider subProvider) {
        Logger.logger.finer("in getProviderName");
        String providerFullClassName = subProvider.getClass().toString().replace("SubProvider", "");
        String provider = providerFullClassName.substring(providerFullClassName.lastIndexOf('.') + 1);
        return provider;
    }
}
