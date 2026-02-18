package com.pg85.otg.dimensions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pg85.otg.util.OTGLog;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DimensionStorage {
    private static final String STORAGE_FILE = "otg_dimensions.json";
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Data
    @NoArgsConstructor
    public static class StorageData {
        @JsonProperty("version")
        private int version = 1;

        @JsonProperty("dimensions")
        private List<DimensionInfo> dimensions = new ArrayList<>();
    }

    private final Path worldPath;
    private StorageData data;

    public DimensionStorage(Path worldPath) {
        this.worldPath = worldPath;
        this.data = new StorageData();
    }

    public void load() {
        Path storagePath = worldPath.resolve(STORAGE_FILE);
        if (!Files.exists(storagePath)) {
            data = new StorageData();
            return;
        }

        try {
            String json = Files.readString(storagePath);
            data = mapper.readValue(json, StorageData.class);
            OTGLog.info("Loaded {} OTG dimensions from storage", data.getDimensions().size());
        } catch (IOException e) {
            OTGLog.error("Failed to load dimension storage, starting fresh: {}", e.getMessage());
            // Backup corrupt file
            try {
                Files.move(storagePath, storagePath.resolveSibling(STORAGE_FILE + ".backup"));
            } catch (IOException ignored) { OTGLog.error("Failed to backup corrupt storage file: {}", ignored.getMessage()); }
            data = new StorageData();
        }
    }

    public void save() {
        Path storagePath = worldPath.resolve(STORAGE_FILE);
        try {
            String json = mapper.writeValueAsString(data);
            Files.writeString(storagePath, json);
        } catch (IOException e) {
            OTGLog.error("Failed to save dimension storage: {}", e.getMessage());
        }
    }

    public void addDimension(DimensionInfo info) {
        data.getDimensions().add(info);
        save();
    }

    public boolean removeDimension(String name) {
        boolean removed = data.getDimensions().removeIf(d -> d.getName().equals(name));
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<DimensionInfo> getDimension(String name) {
        return data.getDimensions().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst();
    }

    public List<DimensionInfo> getAllDimensions() {
        return new ArrayList<>(data.getDimensions());
    }

    public boolean exists(String name) {
        return data.getDimensions().stream()
                .anyMatch(d -> d.getName().equals(name));
    }
}
