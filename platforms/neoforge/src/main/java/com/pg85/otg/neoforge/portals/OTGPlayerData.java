package com.pg85.otg.neoforge.portals;

public class OTGPlayerData {
    private boolean inPortal;
    private String portalColor = "";
    private int portalTime;

    public boolean isInPortal() { return inPortal; }
    public void setInPortal(boolean inPortal) { this.inPortal = inPortal; }
    public String getPortalColor() { return portalColor; }
    public void setPortalColor(String color) { this.portalColor = color; }
    public int getPortalTime() { return portalTime; }
    public void setPortalTime(int time) { this.portalTime = time; }

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
