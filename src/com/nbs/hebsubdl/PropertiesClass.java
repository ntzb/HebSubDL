package com.nbs.hebsubdl;

import java.io.*;
import java.util.HashMap;
import java.util.Properties;

public class PropertiesClass {
    private static String ktuvitUsername;
    private static String ktuvitPassword;
    private static String langSuffix;

    public static void writeProperties(String property, String value) {
        // for a single property
        try (OutputStream output = new FileOutputStream("config.properties")) {

            Properties prop = new Properties();
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
            }
            // set the property value
            prop.setProperty(property, value);

            // save properties to project root folder
            prop.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        }
    }
    public static void writeProperties(HashMap<String, String> properties) {
        // for multiple properties
        try (OutputStream output = new FileOutputStream("config.properties")) {
            Properties prop = new Properties();

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
                }
            }

            // save properties to project root folder
            prop.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public static void readProperties() {
        try (InputStream inputStream = new FileInputStream("config.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            setKtuvitPassword(properties.getProperty("ktuvit.password"));
            setKtuvitUsername(properties.getProperty("ktuvit.username"));
            setLangSuffix(properties.getProperty("language.suffix"));
        }
        catch (IOException e) {
            e.printStackTrace();
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
}
