package com.pg85.otg.neoforge.portals;

public class OTGPlayerData implements com.pg85.otg.shared.portals.IPortalPlayerData {
    private boolean inPortal;
    private String portalColor = "";
    private int portalTime;

    public boolean isInPortal() { return inPortal; }
    public void setInPortal(boolean inPortal) { this.inPortal = inPortal; }
    public String getPortalColor() { return portalColor; }
    public void setPortalColor(String color) { this.portalColor = color; }
    @Override public int getPortalTime() { return portalTime; }
    @Override public void setPortalTime(int time) { this.portalTime = time; }

    @Override
    public void setPortalState(boolean inPortal, String color) {
        this.inPortal = inPortal;
        this.portalColor = color;
    }

    public void tick() {
        if (inPortal) {
            portalTime++;
        } else {
            if (portalTime > 0) portalTime -= 4;
            if (portalTime < 0) portalTime = 0;
        }
        inPortal = false;
    }
}
