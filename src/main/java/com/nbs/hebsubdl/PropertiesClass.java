package com.nbs.hebsubdl;

import java.io.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;

public class PropertiesClass {
    private static String ktuvitUsername;
    private static String ktuvitPassword;
    private static String langSuffix;
    private static String logLevel;
    private static String openSubtitlesUserAgent;
    private static String openSubtitlesUsername;
    private static String openSubtitlesPassword;
    private static String watchDirectories;
    private static String watchIgnoreKeywords;

    // single property
    public static void writeProperties(String property, String value) {
        // for a single property
        FileOutputStream fileOut = null;
        FileInputStream fileIn;
        try {
            Properties prop = new Properties();
            File propFile = new File("config.properties");
            fileIn = new FileInputStream(propFile);
            prop.load(fileIn);

            switch (property) {
                case "ktuvitUsername" -> {
                    setKtuvitUsername(value);
                    prop.setProperty("ktuvit.username", getKtuvitUsername());
                }
                case "ktuvitPassword" -> {
                    setKtuvitPassword(value);
                    prop.setProperty("ktuvit.password", getKtuvitPassword());
                }
                case "langSuffix" -> {
                    setLangSuffix(value);
                    prop.setProperty("language.suffix", getLangSuffix());
                }
                case "openSubtitlesUserAgent" -> {
                    setOpenSubtitlesUserAgent(value);
                    prop.setProperty("opensubtitles.useragnet", getOpenSubtitlesUserAgent());
                }
                case "openSubtitlesUsername" -> {
                    setOpenSubtitlesUsername(value);
                    prop.setProperty("opensubtitles.username", getOpenSubtitlesUsername());
                }
                case "openSubtitlesPassword" -> {
                    setOpenSubtitlesPassword(value);
                    prop.setProperty("opensubtitles.password", getOpenSubtitlesPassword());
                }
                case "watchDirectories" -> {
                    setWatchDirectories(value);
                    prop.setProperty("watch.directories", getWatchDirectories());
                }
                case "watchIgnoreKeywords" -> {
                    setWatchIgnoreKeywords(value);
                    prop.setProperty("watch.ignorekeywords", getWatchIgnoreKeywords());
                }
            }
            // set the property value
            prop.setProperty(property, value);

            // save properties to project root folder
            fileOut = new FileOutputStream(propFile);
            prop.store(fileOut, null);

        } catch (IOException io) {
            Logger.logException(io, "writing to properties file.");
        } finally {
            try {
                fileOut.close();
            } catch (IOException ex) {
                Logger.logException(ex, "failed closing properties file.");
            }
        }
    }

    // multiple properties
    public static void writeProperties(HashMap<String, String> properties) {
        // for multiple properties
        FileOutputStream fileOut = null;
        FileInputStream fileIn;
        try {
            Properties prop = new Properties();
            File propFile = new File("config.properties");
            fileIn = new FileInputStream(propFile);
            prop.load(fileIn);

            // set the properties values
            for (String key : properties.keySet()) {
                switch (key) {
                    case "ktuvitUsername" -> {
                        setKtuvitUsername(properties.get(key));
                        prop.setProperty("ktuvit.username", getKtuvitUsername());
                    }
                    case "ktuvitPassword" -> {
                        setKtuvitPassword(properties.get(key));
                        prop.setProperty("ktuvit.password", getKtuvitPassword());
                    }
                    case "langSuffix" -> {
                        setLangSuffix(properties.get(key));
                        prop.setProperty("language.suffix", getLangSuffix());
                    }
                    case "openSubtitlesUserAgent" -> {
                        setOpenSubtitlesUserAgent(properties.get(key));
                        prop.setProperty("opensubtitles.useragnet", getOpenSubtitlesUserAgent());
                    }
                    case "openSubtitlesUsername" -> {
                        setOpenSubtitlesUsername(properties.get(key));
                        prop.setProperty("opensubtitles.username", getOpenSubtitlesUsername());
                    }
                    case "openSubtitlesPassword" -> {
                        setOpenSubtitlesPassword(properties.get(key));
                        prop.setProperty("opensubtitles.password", getOpenSubtitlesPassword());
                    }
                    case "watchDirectories" -> {
                        setWatchDirectories(properties.get(key));
                        prop.setProperty("watch.directories", getWatchDirectories());
                    }
                    case "watchIgnoreKeywords" -> {
                        setWatchIgnoreKeywords(properties.get(key));
                        prop.setProperty("watch.ignorekeywords", getWatchIgnoreKeywords());
                    }
                }
            }

            // save properties to project root folder
            fileOut = new FileOutputStream(propFile);
            prop.store(fileOut, null);

        } catch (IOException io) {
            Logger.logException(io, "writing to properties file.");
        } finally {
            try {
                fileOut.close();
            } catch (IOException ex) {
                Logger.logException(ex, "failed closing properties file.");
            }
        }
    }

    public static void readProperties() {
        //try (InputStream inputStream = new FileInputStream("config.properties")) {
        FileInputStream fileIn = null;
        try {
            File propFile = new File("config.properties");
            if (!propFile.exists())
                propFile.createNewFile();
            fileIn = new FileInputStream(propFile);
            Properties properties = new Properties();
            properties.load(fileIn);

            setKtuvitPassword(properties.getProperty("ktuvit.password"));
            setKtuvitUsername(properties.getProperty("ktuvit.username"));
            setLangSuffix(properties.getProperty("language.suffix"));
            // open subtitles useragent - if it's empty, use "TemporaryUserAgent" and decide later between this login and alternate login
            String useragent = properties.getProperty("opensubtitles.useragnet");
            setOpenSubtitlesUserAgent((useragent == null || useragent.trim().isEmpty()) ? "TemporaryUserAgent" : useragent);
            setOpenSubtitlesUsername(properties.getProperty("opensubtitles.username"));
            setOpenSubtitlesPassword(properties.getProperty("opensubtitles.password"));
            setLogLevel(properties.getProperty("log.level"));
            setWatchDirectories(properties.getProperty("watch.directories"));
            setWatchIgnoreKeywords(properties.getProperty("watch.ignorekeywords"));
        }
        catch (Exception e) {
            Logger.logException(e, "reading properties file");
        } finally {
            try {
                fileIn.close();
            } catch (IOException ex) {
                Logger.logException(ex, "failed closing properties file.");
            }
        }
    }

    public static String getKtuvitUsername() {
        return ktuvitUsername;
    }

    public static void setKtuvitUsername(String ktuvitUsername) {
        PropertiesClass.ktuvitUsername = ktuvitUsername;
    }

    public static String getKtuvitPassword() {
        return ktuvitPassword;
    }

    public static void setKtuvitPassword(String ktuvitPassword) {
        PropertiesClass.ktuvitPassword = ktuvitPassword;
    }

    public static String getLangSuffix() {
        return langSuffix;
    }

    public static void setLangSuffix(String langSuffix) {
        PropertiesClass.langSuffix = langSuffix;
    }

    public static String getLogLevel() {
        return logLevel;
    }

    public static void setLogLevel(String logLevel) {
        PropertiesClass.logLevel = logLevel;
        if (logLevel != null) {
            switch (logLevel.toLowerCase()) {
                case "severe", "error" -> Logger.logger.setLevel(Level.SEVERE);
                case "warning" -> {
                    Logger.logger.setLevel(Level.WARNING);
                    Logger.logger.warning("log level will be set to WARNING");
                }
                case "info" -> {
                    Logger.logger.setLevel(Level.INFO);
                    Logger.logger.info("log level will be set to INFO");
                }
                case "fine" -> {
                    Logger.logger.setLevel(Level.FINE);
                    Logger.logger.info("log level will be set to FINE");
                }
                case "finer" -> {
                    Logger.logger.setLevel(Level.FINER);
                    Logger.logger.info("log level will be set to FINER");
                }
                case "finest", "debug" -> {
                    Logger.logger.setLevel(Level.FINEST);
                    Logger.logger.info("log level will be set to FINEST");
                }
            }
        }
    }

    public static String getOpenSubtitlesUserAgent() {
        return openSubtitlesUserAgent;
    }

    public static void setOpenSubtitlesUserAgent(String openSubtitlesUserAgent) {
        PropertiesClass.openSubtitlesUserAgent = openSubtitlesUserAgent;
    }

    public static String getWatchDirectories() {
        return watchDirectories;
    }

    public static void setWatchDirectories(String watchDirectories) {
        PropertiesClass.watchDirectories = watchDirectories;
    }

    public static String getWatchIgnoreKeywords() {
        return watchIgnoreKeywords;
    }

    public static void setWatchIgnoreKeywords(String watchIgnoreKeywords) {
        PropertiesClass.watchIgnoreKeywords = watchIgnoreKeywords;
    }

    public static String getOpenSubtitlesUsername() {
        return openSubtitlesUsername;
    }

    public static void setOpenSubtitlesUsername(String openSubtitlesUsername) {
        PropertiesClass.openSubtitlesUsername = openSubtitlesUsername;
    }

    public static String getOpenSubtitlesPassword() {
        return openSubtitlesPassword;
    }

    public static void setOpenSubtitlesPassword(String openSubtitlesPassword) {
        PropertiesClass.openSubtitlesPassword = openSubtitlesPassword;
    }
}
