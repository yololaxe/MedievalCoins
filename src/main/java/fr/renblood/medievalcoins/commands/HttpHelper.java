package fr.renblood.medievalcoins.commands;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpHelper {
    public static boolean ping(String baseUrl) {
        try {
            URL url = new URL(baseUrl + "/ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(2_000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return (200 <= code && code < 300);
        } catch (IOException e) {
            return false;
        }
    }
}
