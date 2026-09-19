package subapi;

final class SubsonicConfig {
    String serverUrl = "";
    String username = "";
    String password = "";
    String clientName = "AllMusic-SubAPI";
    String apiVersion = "1.16.1";
    boolean debug = false;
    String authentication = "md5";

    boolean isValid() {
        return serverUrl != null && !serverUrl.isEmpty()
                && username != null && !username.isEmpty()
                && password != null && !password.isEmpty();
    }
}
