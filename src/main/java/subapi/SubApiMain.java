package subapi;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubApiMain implements IMusicApi {
    private SubsonicConfig config = new SubsonicConfig();
    private SubsonicClient client = new SubsonicClient(config, SubApiMain::log);
    private volatile boolean busy;

    @Override
    public void reload(File path) {
        File file = new File(path, "subapi.json");
        try {
            if (file.exists()) {
                try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    SubsonicConfig loaded = AllMusic.gson.fromJson(reader, SubsonicConfig.class);
                    if (loaded != null) {
                        config = loaded;
                    }
                }
            } else {
                Files.write(file.toPath(), AllMusic.gson.toJson(config).getBytes(StandardCharsets.UTF_8));
            }
            config.serverUrl = normalizeUrl(config.serverUrl);
            if (config.username == null) config.username = "";
            if (config.password == null) config.password = "";
            if (config.clientName == null || config.clientName.isEmpty()) {
                config.clientName = "AllMusic-SubAPI";
            }
            if (config.apiVersion == null || config.apiVersion.isEmpty()) {
                config.apiVersion = "1.16.1";
            }
            if (!"password".equalsIgnoreCase(config.authentication)
                    && !"md5".equalsIgnoreCase(config.authentication)) {
                log("authentication 配置无效，使用 md5");
                config.authentication = "md5";
            } else {
                config.authentication = config.authentication.toLowerCase();
            }
            client = new SubsonicClient(config, SubApiMain::log);
            if (!config.isValid()) {
                log("请在 subapi.json 中配置 serverUrl、username 和 password");
            } else {
                testConnection();
            }
        } catch (JsonParseException e) {
            log("subapi.json 格式错误：" + e.getMessage());
        } catch (Exception e) {
            log("读取配置失败：" + e.getMessage());
        }
    }

    private void testConnection() {
        busy = true;
        try {
            client.ping();
            log("Subsonic 服务连接测试成功：" + config.serverUrl);
        } catch (Exception e) {
            log("Subsonic 服务连接测试失败：" + e.getMessage());
        } finally {
            busy = false;
        }
    }

    @Override
    public String getId() {
        return "subapi";
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        try {
            JsonObject song = client.request("getSong", params("id", id)).getAsJsonObject("song");
            return song == null ? null : toSong(song, player, isList);
        } catch (Exception e) {
            log("获取歌曲信息失败：" + e.getMessage());
            return null;
        }
    }

    @Override
    public SearchPageObj search(String[] words) {
        if (words == null || words.length == 0) {
            return new SearchPageObj(Collections.<SearchMusicObj>emptyList(), 0, getId());
        }
        try {
            Map<String, String> params = params("query", join(words));
            params.put("songCount", "30");
            JsonObject result = client.request("search2", params);
            JsonObject searchResult = result.has("searchResult2")
                    && result.get("searchResult2").isJsonObject()
                    ? result.getAsJsonObject("searchResult2") : result;
            List<SearchMusicObj> songs = new ArrayList<>();
            for (JsonObject song : SubsonicClient.array(searchResult, "song")) {
                songs.add(new SearchMusicObj(value(song, "id"), value(song, "title"),
                        value(song, "artist"), value(song, "album")));
            }
            if (songs.isEmpty()) {
                return null;
            }
            return new SearchPageObj(songs, (songs.size() + 9) / 10, getId());
        } catch (Exception e) {
            log("搜索歌曲失败：" + e.getMessage());
            return null;
        }
    }

    @Override
    public String getPlayUrl(String id) {
        if (!config.isValid() || id == null) {
            return null;
        }
        try {
            JsonObject song = client.request("getSong", params("id", id)).getAsJsonObject("song");
            if (song == null || !isMpeg(song)) {
                return null;
            }
            return client.streamUrl(id);
        } catch (Exception e) {
            log("检查歌曲格式失败：" + e.getMessage());
            return null;
        }
    }

    @Override
    public LyricSave getLyric(String id) {
        return new LyricSave();
    }

    @Override
    public void setList(String id, Object sender) {
        log("Subsonic 歌单导入暂未实现");
    }

    @Override public boolean isBusy() { return busy; }
    @Override public String getMusicId(String id) { return id; }
    @Override public boolean checkId(String id) { return id != null && !id.trim().isEmpty(); }
    @Override public void command(Object sender, String command, String[] args) { }
    @Override public List<String> tab(Object sender, String command, String[] args) { return new ArrayList<>(); }

    private SongInfoObj toSong(JsonObject song, String player, boolean isList) {
        long length = song.has("duration") ? song.get("duration").getAsLong() * 1000L : 0L;
        return new SongInfoObj(value(song, "artist"), value(song, "title"), value(song, "id"),
                "", player, value(song, "album"), isList, length, value(song, "coverArt"),
                false, null, getId());
    }

    private static boolean isMpeg(JsonObject song) {
        String suffix = value(song, "suffix");
        String contentType = value(song, "contentType");
        return "mp3".equalsIgnoreCase(suffix)
                || "audio/mpeg".equalsIgnoreCase(contentType)
                || "audio/mp3".equalsIgnoreCase(contentType);
    }

    private static Map<String, String> params(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }

    private static String join(String[] words) {
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word != null && !word.trim().isEmpty()) {
                if (result.length() > 0) result.append(' ');
                result.append(word.trim());
            }
        }
        return result.toString();
    }

    private static String value(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private static String normalizeUrl(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static void log(String message) {
        if (AllMusic.log != null) {
            AllMusic.log.data("<light_purple>[SubAPI]<yellow>" + message);
        }
    }
}
