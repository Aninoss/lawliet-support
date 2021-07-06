package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

public class SettingsManager {

    private final JSONObject data;

    public SettingsManager() throws IOException {
        Path configFile = Path.of(System.getenv("CONFIG_FOLDER") + "/config.json");
        String jsonContent = Files.readString(configFile);
        this.data = new JSONObject(jsonContent);
    }

    public JSONObject get() {
        return data;
    }

}
