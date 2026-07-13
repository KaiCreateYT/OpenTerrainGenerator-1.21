package com.pg85.otg.client.editor.data;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class BiomeGroupData {
    private String name;
    private int generationDepth;
    private int rarity;
    private final List<String> biomes;
    private double minTemp;
    private double maxTemp;
    private boolean dirty;
    private boolean isNew;
    private boolean deleted;

    public BiomeGroupData(String name, int generationDepth, int rarity, List<String> biomes,
                          double minTemp, double maxTemp) {
        this.name = name;
        this.generationDepth = generationDepth;
        this.rarity = rarity;
        this.biomes = new ArrayList<>(biomes);
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
    }

    public String getName() { return name; }
    public int getGenerationDepth() { return generationDepth; }
    public int getRarity() { return rarity; }
    public List<String> getBiomes() { return biomes; }
    public double getMinTemp() { return minTemp; }
    public double getMaxTemp() { return maxTemp; }
    public boolean isDirty() { return dirty; }
    public boolean isNew() { return isNew; }
    public boolean isDeleted() { return deleted; }

    public void setName(String name) { this.name = name; this.dirty = true; }
    public void setGenerationDepth(int generationDepth) { this.generationDepth = generationDepth; this.dirty = true; }
    public void setRarity(int rarity) { this.rarity = rarity; this.dirty = true; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; this.dirty = true; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; this.dirty = true; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; this.dirty = true; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public void clearDirty() { this.dirty = false; }

    public String toConfigLine() {
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add(name);
        joiner.add(String.valueOf(generationDepth));
        joiner.add(String.valueOf(rarity));
        for (String biome : biomes) {
            joiner.add(biome);
        }
        if (minTemp != 0.0 || maxTemp != 0.0) {
            joiner.add(String.valueOf(minTemp));
            joiner.add(String.valueOf(maxTemp));
        }
        return "BiomeGroup(" + joiner + ")";
    }
}
