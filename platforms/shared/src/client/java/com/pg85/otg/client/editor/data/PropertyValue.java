package com.pg85.otg.client.editor.data;

import java.util.Objects;

public class PropertyValue {
    private final PropertyDefinition definition;
    private String value;
    private boolean override;
    private boolean merge;
    private boolean opv;
    private boolean dirty;

    public PropertyValue(PropertyDefinition definition, String value) {
        this.definition = definition;
        this.value = value;
    }

    public PropertyDefinition getDefinition() { return definition; }
    public String getValue() { return value; }
    public boolean isOverride() { return override; }
    public boolean isMerge() { return merge; }
    public boolean isOpv() { return opv; }
    public boolean isDirty() { return dirty; }

    public void setValue(String value) {
        if (!Objects.equals(this.value, value)) {
            this.value = value;
            this.dirty = true;
        }
    }

    public void setOverride(boolean override) { this.override = override; this.dirty = true; }
    public void setMerge(boolean merge) { this.merge = merge; this.dirty = true; }
    public void setOpv(boolean opv) { this.opv = opv; this.dirty = true; }
    public void clearDirty() { this.dirty = false; }
}
