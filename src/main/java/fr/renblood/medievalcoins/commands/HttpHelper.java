package fr.renblood.medievalcoins.commands;

public class HttpHelper {
    public static boolean ping(String baseUrl) {
        try {
            fr.renblood.medievalcoins.network.ApiHttpClient.get(baseUrl + "/ping");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
