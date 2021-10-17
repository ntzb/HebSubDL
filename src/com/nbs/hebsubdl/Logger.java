package com.nbs.hebsubdl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.*;

public class Logger {
    public static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("HebSubDL");

    public static void initLogger() {
        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler("log.log", 1000000, 2,true);
            logger.addHandler(fileHandler);

            logger.setLevel(Level.FINEST);
            fileHandler.setFormatter(new SimpleFormatter() {
                private static final String format
                        = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

                // Override format method
                @Override
                public synchronized String format(
                        LogRecord logRecord)
                {
                    return String.format(
                            format, new Date(logRecord.getMillis()),
                            logRecord.getLevel().getLocalizedName(),
                            logRecord.getMessage());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logException(Exception e, String action) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        logger.severe("caught exception while " + action + ", printing stack trace:");
        logger.severe(exceptionAsString);
    }
}
