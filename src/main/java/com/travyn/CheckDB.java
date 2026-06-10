package com.travyn;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDB {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://ep-silent-fire-a5v594x7.us-east-2.aws.neon.tech/travyn?sslmode=require";
        try (Connection conn = DriverManager.getConnection(url, "travyn_owner", "yv1Pj5qNExbB");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT token, is_active FROM sos_tokens")) {
            
            while (rs.next()) {
                System.out.println("Token: " + rs.getString("token") + ", Active: " + rs.getBoolean("is_active"));
            }
        }
    }
}
