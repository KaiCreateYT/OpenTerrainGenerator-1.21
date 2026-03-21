package com.pg85.otg.client.editor.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class GroupOverrideStore {

    private static final Logger LOG = LoggerFactory.getLogger(GroupOverrideStore.class);
    private static final String FILE_NAME = ".otg-editor.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record GroupPropertyOverride(String value, boolean override, boolean merge, boolean opv) {}

    public static Map<String, Map<String, GroupPropertyOverride>> load(Path presetFolder) {
        Path file = presetFolder.resolve(FILE_NAME);
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            String json = Files.readString(file);
            Type type = new TypeToken<StoreRoot>(){}.getType();
            StoreRoot root = GSON.fromJson(json, type);
            return root != null && root.groups != null ? root.groups : new LinkedHashMap<>();
        } catch (Exception e) {
            LOG.warn("Failed to read {}, treating as empty", FILE_NAME, e);
            return new LinkedHashMap<>();
        }
    }

    public static void save(Path presetFolder, Map<String, Map<String, GroupPropertyOverride>> overrides) {
        Path file = presetFolder.resolve(FILE_NAME);
        StoreRoot root = new StoreRoot();
        root.groups = overrides;
        try {
            Files.writeString(file, GSON.toJson(root));
            LOG.info("Saved group overrides to {}", file.getFileName());
        } catch (IOException e) {
            LOG.error("Failed to write {}", FILE_NAME, e);
        }
    }

    private static class StoreRoot {
        Map<String, Map<String, GroupPropertyOverride>> groups;
    }
}
