package com.pg85.otg.fabric.portals.components;

import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public interface OTGPlayerComponent extends Component, AutoSyncedComponent, ServerTickingComponent {

    boolean isInPortal();
    void setInPortal(boolean inPortal);

    String getPortalColor();
    void setPortalColor(String color);

    int getPortalTime();
    void setPortalTime(int time);
    void incrementPortalTime();

    void setPortalState(boolean inPortal, String color);
}
