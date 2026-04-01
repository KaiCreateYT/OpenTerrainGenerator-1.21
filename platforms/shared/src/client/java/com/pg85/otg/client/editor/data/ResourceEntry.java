package com.pg85.otg.client.editor.data;

public class ResourceEntry {

    private String line;
    private boolean dirty;
    private boolean deleted;

    public ResourceEntry(String line) {
        this.line = line.trim();
    }

    public String getLine() { return line; }
    public boolean isDirty() { return dirty; }
    public boolean isDeleted() { return deleted; }

    public void setLine(String line) {
        if (!this.line.equals(line)) {
            this.line = line.trim();
            this.dirty = true;
        }
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        this.dirty = true;
    }

    public void clearDirty() { this.dirty = false; }

    public String getFunctionName() {
        int parenIdx = line.indexOf('(');
        return parenIdx > 0 ? line.substring(0, parenIdx) : line;
    }
}
