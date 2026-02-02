package com.pg85.otg.fabric.portals.components;

import dev.onyxstudios.cca.api.v3.component.Component;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;

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
