package com.nbs.hebsubdl;

import java.sql.*;

public class DbAccess {

    // create DB file, or open it if already created
    private boolean openOrCreateDB(String dbPath) {
        String url = "jdbc:sqlite:" + dbPath;

        try {
            this.conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    // check DB to see if we have a valid cookie. if not - generate one
    public boolean loginValid() {
        String dbPath = "test.sqlite";

        String getLoginSql = "CREATE TABLE IF NOT EXISTS login (\n"
                + "	cookie text PRIMARY KEY,\n"
                + "	validUntil integer NOT NULL\n"
                + ");";

        if (this.getConn() == null) {
            if (!openOrCreateDB(dbPath))
                return false;
        }

        try (Statement stmt = this.getConn().createStatement()) {
            // create a new table if not exists
            stmt.execute(getLoginSql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

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
                    loginNeeded = isCookieValid();
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }

        return !loginNeeded;
    }

    // update the DB with new login details
    public boolean insertLogin (String cookie, long validUntil) {
        String sql = "INSERT INTO login(cookie, validUntil) VALUES(?,?)";

        try (PreparedStatement pstmt = this.getConn().prepareStatement(sql)) {
            pstmt.setString(1, cookie);
            pstmt.setLong(2, validUntil);
            pstmt.executeUpdate();
            this.cookie = cookie;
            this.validUntil = validUntil;
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean isCookieValid() {
        long currentTime = System.currentTimeMillis()/1000;
        return (this.validUntil + this.operationTime < currentTime);
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
