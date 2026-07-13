package com.pg85.otg.shared.portals;

public interface IPortalPlayerData {
    void setPortalState(boolean inPortal, String color);
    int getPortalTime();
    void setPortalTime(int time);
}
