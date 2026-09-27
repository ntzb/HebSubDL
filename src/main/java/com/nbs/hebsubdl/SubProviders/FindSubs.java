package com.nbs.hebsubdl.SubProviders;

import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MainGUI;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FindSubs {
    // replaced whole, never modified, so a search running while settings are
    // saved keeps iterating the list it started with
    static volatile List<ISubProvider> providersList = Collections.emptyList();

    // Providers read their credentials in the constructor and cache tokens for
    // hours, so the list has to be rebuilt when settings change or the old
    // values stay live until the app is restarted.
    public static void reinitProviders() {
        Logger.logger.info("credentials changed, rebuilding providers");
        initProviders();
    }

    public static void initProviders() {
        Logger.logger.finer("initializing providers");
        List<ISubProvider> providers = new ArrayList<>();
        providers.add(new WizdomSubProvider());
        providers.add(new KtuvitSubProvider());
        providers.add(new OpensubtitlesNewSubProvider());
        providersList = Collections.unmodifiableList(providers);

        providersList.forEach(provider -> {
            Logger.logger.info(String.format("provider %s added", getProviderName(provider)));
        });
    }

    // synchronized: a watched folder and a manual run can both start a search,
    // and the providers keep per-search state (Ktuvit's session and film id)
    public static synchronized void findSubs(ArrayList<MediaFile> mediaFileList, DefaultTableModel model, JTable jTable, int run) {
        Logger.logger.info("will search subtitles for " + mediaFileList.size() + " items.");

        for (int count = 0; count < mediaFileList.size(); count++) {
            MediaFile mediaFile = mediaFileList.get(count);
            try {
                Logger.logger.info("searching subtitles for item: " + mediaFile.getFileName());
                if (subAlreadyExists(mediaFile)) {
                    Logger.logger.info("subtitle already exists! " + mediaFile.getFileName());
                    setStatus(model, jTable, run, count, "sub already exists");
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
                        setStatus(model, jTable, run, count, "downloading..");
                        Logger.logger.info(String.format("downloading sub from %s (%s)", provider,
                                subProvider.getChosenSubName()));
                        didDownload = subProvider.downloadSubFile(currentRatingSub[0], mediaFile);
                        if (didDownload) {
                            Logger.logger.info("sub downloaded!");
                            setStatus(model, jTable, run, count, "success!");
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
                                setStatus(model, jTable, run, count, "success!");
                                didDownload = true;
                                break;
                            } else {
                                Logger.logger.warning("provider " + provider + "failed, trying the next one.");
                                setStatus(model, jTable, run, count, "failed provider, trying next one..");
                            }
                        }
                        if (!didDownload) {
                            Logger.logger.warning("all providers failed, something wrong?");
                            setStatus(model, jTable, run, count, "all providers failed, something wrong?");
                        }
                    } else {
                        // no match at all
                        Logger.logger.warning("failed - didn't find a matching sub.");
                        setStatus(model, jTable, run, count, "failed - didn't find a match.");
                    }
                } else if (!didDownload) {
                    // no match at all
                    Logger.logger.warning("failed - no providers available.");
                    setStatus(model, jTable, run, count, "failed - no providers available.");
                }
            } catch (Exception e) {
                setStatus(model, jTable, run, count, "failed - error during search.");
                Logger.logException(e, "error during search");
            }
        }
    }

    // runs off the worker thread, and Swing models must only be touched on the EDT
    private static void setStatus(DefaultTableModel model, JTable jTable, int run, int row, String status) {
        SwingUtilities.invokeLater(() -> {
            if (!MainGUI.isCurrentRun(run))
                return;
            model.setValueAt(status, row, 1);
            MainGUI.refreshTable(jTable);
        });
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
