package com.nbs.hebsubdl;

import java.sql.*;

public class DbAccess {

    // create DB file, or open it if already created
    private boolean openOrCreateDB(String dbPath) {
        String url = "jdbc:sqlite:" + dbPath;

        try {
            this.conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            Logger.logException(e, "connecting to the DB.");
            return false;
        }
        return true;
    }

    private boolean openWithLoginTable() {
        if (this.getConn() == null && !openOrCreateDB("db.sqlite"))
            return false;

        String createLoginSql = "CREATE TABLE IF NOT EXISTS login (\n"
                + "	cookie text PRIMARY KEY,\n"
                + "	validUntil integer NOT NULL\n"
                + ");";
        try (Statement stmt = this.getConn().createStatement()) {
            stmt.execute(createLoginSql);
        } catch (SQLException e) {
            Logger.logException(e, "creating the login table.");
            return false;
        }
        return true;
    }

    // check DB to see if we have a valid cookie. if not - generate one
    public boolean loginValid() {
        if (!openWithLoginTable())
            return false;

        String sqlSelect = "SELECT validUntil, cookie FROM login";
        boolean loginNeeded = false;

        try (Statement stmt = this.getConn().createStatement();
             ResultSet resultSet = stmt.executeQuery(sqlSelect)) {

            // loop through the result set
            if (!resultSet.isBeforeFirst()) {
                loginNeeded = true;
            }
            else {
                while (resultSet.next()) {
                    this.validUntil = resultSet.getLong("validUntil");
                    this.cookie = resultSet.getString("cookie");
                    loginNeeded = isCookieExpired();
                }
            }
        } catch (SQLException e) {
            Logger.logException(e, "creating statement or executing query, for login.");
            return false;
        }

        return !loginNeeded;
    }

    // update the DB with new login details, replacing the previous login
    public boolean insertLogin (String cookie, long validUntil) {
        String sql = "INSERT INTO login(cookie, validUntil) VALUES(?,?)";

        try (Statement stmt = this.getConn().createStatement();
             PreparedStatement pstmt = this.getConn().prepareStatement(sql)) {
            stmt.execute("DELETE FROM login");
            pstmt.setString(1, cookie);
            pstmt.setLong(2, validUntil);
            pstmt.executeUpdate();
            this.cookie = cookie;
            this.validUntil = validUntil;
            return true;
        } catch (SQLException e) {
            Logger.logException(e, "inserting new login to DB.");
            return false;
        }
    }

    // the cookie outlives a credential change, and it is stored on disk, so it
    // survives a restart too - without this, new Ktuvit credentials do nothing
    // until the old session expires
    public boolean clearLogin() {
        if (!openWithLoginTable())
            return false;
        try (Statement stmt = this.getConn().createStatement()) {
            stmt.execute("DELETE FROM login");
            this.cookie = null;
            this.validUntil = 0;
            Logger.logger.info("cleared the stored Ktuvit login");
            return true;
        } catch (SQLException e) {
            Logger.logException(e, "clearing the stored Ktuvit login.");
            return false;
        }
    }

    // validUntil is in milliseconds; treat the cookie as expired a minute
    // early so it doesn't run out mid-download
    public boolean isCookieExpired() {
        return this.validUntil - this.operationTime * 1000L < System.currentTimeMillis();
    }

    public void close() {
        if (this.conn == null)
            return;
        try {
            this.conn.close();
        } catch (SQLException e) {
            Logger.logException(e, "closing the DB.");
        }
        this.conn = null;
    }

    private Connection conn;
    private String cookie;
    private long validUntil;
    private final int operationTime = 60;

    public Connection getConn() {
        return conn;
    }

    public String getCookie() {
        return cookie;
    }
}
