package com.github.mmin18.layoutcast.ide;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Upload an anonymous report to help improve the cast tool.<br>
 * The uploaded data is very simple and does not include any sensitive info:<p/>
 * POST http://mmin18.cloudant.com/layoutcast/<br>
 * {
 * "castVersion": "1.50823",
 * "exitCode": 0,
 * "timeUsed": 2043
 * }
 * <p/>
 * Created by mmin18 on 8/23/15.
 */
public class StatUtils {

    public static void send(File castPy, int exit, long elapse) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        double ver = 1.0;
        try {
            FileInputStream fis = new FileInputStream(castPy);
            ver = CastAction.readVersion(fis);
            fis.close();
        } catch (Exception e) {
        }
        sb.append("\"castVersion\":\"").append(ver).append("\",");
        sb.append("\"exitCode\":").append(exit).append(",");
        sb.append("\"timeUsed\":").append(elapse).append("}");

        try {
            URL url = new URL("http://mmin18.cloudant.com/layoutcast/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(sb.toString().getBytes("utf-8"));
            int sc = conn.getResponseCode();
            System.out.println(sc);
        } catch (Exception e) {
        }
    }

}
