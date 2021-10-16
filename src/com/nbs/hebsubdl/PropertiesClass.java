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

    public static void writeProperties(String property, String value) {
        // for a single property
        FileOutputStream fileOut = null;
        FileInputStream fileIn = null;
        try {
            //OutputStream output = new FileOutputStream("config.properties")}) {
            Properties prop = new Properties();
            File propFile = new File("config.properties");
            fileIn = new FileInputStream(propFile);
            prop.load(fileIn);

            switch (property) {
                case "ktuvitUsername":
                    setKtuvitUsername(value);
                    prop.setProperty("ktuvit.username", getKtuvitUsername());
                    break;
                case "ktuvitPassword":
                    setKtuvitPassword(value);
                    prop.setProperty("ktuvit.password", getKtuvitPassword());
                    break;
                case "langSuffix":
                    setLangSuffix(value);
                    prop.setProperty("language.suffix", getLangSuffix());
                    break;
                case "openSubtitlesUserAgent":
                    setOpenSubtitlesUserAgent(value);
                    prop.setProperty("opensubtitles.useragnet", getOpenSubtitlesUserAgent());
                    break;
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
    public static void writeProperties(HashMap<String, String> properties) {
        // for multiple properties
        FileOutputStream fileOut = null;
        FileInputStream fileIn = null;
        try {
            //OutputStream output = new FileOutputStream("config.properties")}) {
            Properties prop = new Properties();
            File propFile = new File("config.properties");
            fileIn = new FileInputStream(propFile);
            prop.load(fileIn);

            // set the properties values
            for (String key : properties.keySet()) {
                switch (key) {
                    case "ktuvitUsername":
                        setKtuvitUsername(properties.get(key));
                        prop.setProperty("ktuvit.username", getKtuvitUsername());
                        break;
                    case "ktuvitPassword":
                        setKtuvitPassword(properties.get(key));
                        prop.setProperty("ktuvit.password", getKtuvitPassword());
                        break;
                    case "langSuffix":
                        setLangSuffix(properties.get(key));
                        prop.setProperty("language.suffix", getLangSuffix());
                        break;
                    case "openSubtitlesUserAgent":
                        setOpenSubtitlesUserAgent(properties.get(key));
                        prop.setProperty("opensubtitles.useragnet", getOpenSubtitlesUserAgent());
                        break;
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
        try (InputStream inputStream = new FileInputStream("config.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            setKtuvitPassword(properties.getProperty("ktuvit.password"));
            setKtuvitUsername(properties.getProperty("ktuvit.username"));
            setLangSuffix(properties.getProperty("language.suffix"));
            setOpenSubtitlesUserAgent(properties.getProperty("opensubtitles.useragnet"));
            setLogLevel(properties.getProperty("log.level"));
        }
        catch (IOException e) {
            Logger.logException(e, "reading properties file");
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
                case "severe":
                case "error":
                    Logger.logger.setLevel(Level.SEVERE);
                    break;
                case "warning":
                    Logger.logger.setLevel(Level.WARNING);
                    Logger.logger.warning("log level will be set to WARNING");
                    break;
                case "info":
                    Logger.logger.setLevel(Level.INFO);
                    Logger.logger.info("log level will be set to INFO");
                    break;
                case "fine":
                    Logger.logger.setLevel(Level.FINE);
                    Logger.logger.info("log level will be set to FINE");
                    break;
                case "finer":
                    Logger.logger.setLevel(Level.FINER);
                    Logger.logger.info("log level will be set to FINER");
                    break;
                case "finest":
                case "debug":
                    Logger.logger.setLevel(Level.FINEST);
                    Logger.logger.info("log level will be set to FINEST");
                    break;
            }
        }
    }

    public static String getOpenSubtitlesUserAgent() {
        return openSubtitlesUserAgent;
    }

    public static void setOpenSubtitlesUserAgent(String openSubtitlesUserAgent) {
        PropertiesClass.openSubtitlesUserAgent = openSubtitlesUserAgent;
    }
}
