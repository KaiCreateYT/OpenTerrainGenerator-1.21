package com.pg85.otg.client.editor.data;

import java.util.List;

public record PropertyDefinition(
    String name,
    PropertyType type,
    PropertyCategory category,
    String defaultValue,
    String min,
    String max,
    List<String> enumValues,
    String comment
) {}
