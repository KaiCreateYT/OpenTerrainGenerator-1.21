package com.pg85.otg.fabric.portals.components;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class OTGPlayerComponentImpl implements OTGPlayerComponent {

    private final Player player;
    private boolean inPortal = false;
    private String portalColor = "default";
    private int portalTime = 0;

    // Return position for portal linking
    private String returnDimension = "";
    private int returnX = 0;
    private int returnY = 0;
    private int returnZ = 0;

    public OTGPlayerComponentImpl(Player player) {
        this.player = player;
    }

    @Override
    public boolean isInPortal() {
        return inPortal;
    }

    @Override
    public void setInPortal(boolean inPortal) {
        this.inPortal = inPortal;
        if (!inPortal) {
            this.portalTime = 0;
        }
    }

    @Override
    public String getPortalColor() {
        return portalColor;
    }

    @Override
    public void setPortalColor(String color) {
        this.portalColor = color;
    }

    @Override
    public int getPortalTime() {
        return portalTime;
    }

    @Override
    public void setPortalTime(int time) {
        this.portalTime = time;
    }

    @Override
    public void incrementPortalTime() {
        this.portalTime++;
    }

    @Override
    public void setPortalState(boolean inPortal, String color) {
        this.inPortal = inPortal;
        this.portalColor = color;
        if (!inPortal) {
            this.portalTime = 0;
        }
    }

    @Override
    public void serverTick() {
        if (inPortal) {
            portalTime++;
        } else {
            portalTime = 0;
        }
        // Reset inPortal flag - will be set again if still in portal
        inPortal = false;
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        this.inPortal = tag.getBoolean("inPortal");
        this.portalColor = tag.getString("portalColor");
        this.portalTime = tag.getInt("portalTime");
        if (this.portalColor.isEmpty()) {
            this.portalColor = "default";
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("inPortal", inPortal);
        tag.putString("portalColor", portalColor);
        tag.putInt("portalTime", portalTime);
    }
}
