package net.vulkanmod.config;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.video.VideoMode;
import net.vulkanmod.config.video.VideoModeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@JsonAdapter(Config.GsonAdapter.class)
public class Config {

    public VideoMode videoMode;
    public int windowMode = 0;

    public int advCulling = 2;
    public boolean indirectDraw = true;
    public boolean uniqueOpaqueLayer = true;
    public boolean entityCulling = true;

    public int ambientOcclusion = 1;
    public int frameQueueSize = 2;
    public int builderThreads = 0;
    public boolean backFaceCulling = true;
    public boolean textureAnimations = true;

    public int device = -1;

    private static Path CONFIG_PATH;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Config.class, new GsonAdapter())
            .create();

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            Initializer.LOGGER.error("Error saving config file!", e);
        }
    }

    public static Config load(Path path) {
        CONFIG_PATH = path;

        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                Config config = GSON.fromJson(content, Config.class);

                if (config.videoMode == null ||
                        VideoModeManager.findSetFor(config.videoMode) == null) {
                    config.videoMode = VideoModeManager.currentOsMode();
                }

                return config;
            } catch (IOException | JsonSyntaxException e) {
                System.err.println("Failed to load config, using defaults: " + e.getMessage());
            }
        }

        Config config = new Config();
        config.videoMode = VideoModeManager.currentOsMode();
        return config;
    }

    public static class GsonAdapter implements JsonSerializer<Config>, JsonDeserializer<Config> {

        @Override
        public JsonElement serialize(Config src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            if (src.videoMode != null) {
                JsonObject vm = new JsonObject();
                vm.addProperty("width", src.videoMode.width());
                vm.addProperty("height", src.videoMode.height());
                vm.addProperty("bitDepth", src.videoMode.bitDepth());
                vm.addProperty("refreshRate", src.videoMode.refreshRate());
                obj.add("videoMode", vm);
            }

            obj.addProperty("windowMode", src.windowMode);
            obj.addProperty("advCulling", src.advCulling);
            obj.addProperty("indirectDraw", src.indirectDraw);
            obj.addProperty("uniqueOpaqueLayer", src.uniqueOpaqueLayer);
            obj.addProperty("entityCulling", src.entityCulling);
            obj.addProperty("ambientOcclusion", src.ambientOcclusion);
            obj.addProperty("frameQueueSize", src.frameQueueSize);
            obj.addProperty("builderThreads", src.builderThreads);
            obj.addProperty("backFaceCulling", src.backFaceCulling);
            obj.addProperty("textureAnimations", src.textureAnimations);
            obj.addProperty("device", src.device);

            return obj;
        }

        @Override
        public Config deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Config config = new Config();
            JsonObject obj = json.getAsJsonObject();

            if (obj.has("videoMode")) {
                JsonObject vm = obj.getAsJsonObject("videoMode");
                int w = getInt(vm, "width", 1920);
                int h = getInt(vm, "height", 1080);
                int bd = getInt(vm, "bitDepth", 8);
                int rr = getInt(vm, "refreshRate", 60);
                config.videoMode = new VideoMode(w, h, bd, rr);
            } else {
                config.videoMode = VideoModeManager.currentOsMode();
            }

            config.windowMode = getInt(obj, "windowMode", 0);
            config.advCulling = getInt(obj, "advCulling", 2);
            config.indirectDraw = getBoolean(obj, "indirectDraw");
            config.uniqueOpaqueLayer = getBoolean(obj, "uniqueOpaqueLayer");
            config.entityCulling = getBoolean(obj, "entityCulling");
            config.ambientOcclusion = getInt(obj, "ambientOcclusion", 1);
            config.frameQueueSize = getInt(obj, "frameQueueSize", 2);
            config.builderThreads = getInt(obj, "builderThreads", 0);
            config.backFaceCulling = getBoolean(obj, "backFaceCulling");
            config.textureAnimations = getBoolean(obj, "textureAnimations");
            config.device = getInt(obj, "device", -1);

            return config;
        }

        private int getInt(JsonObject obj, String key, int def) {
            JsonElement el = obj.get(key);
            return el != null && el.isJsonPrimitive() ? el.getAsInt() : def;
        }

        private boolean getBoolean(JsonObject obj, String key) {
            JsonElement el = obj.get(key);
            return el == null || !el.isJsonPrimitive() || el.getAsBoolean();
        }
    }
}