package com.pg85.otg.client.editor.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class OverrideResolver {

    private static final Logger LOG = LoggerFactory.getLogger(OverrideResolver.class);

    public static List<PropertyValue> resolve(
            List<PropertyValue> templateProperties,
            String biomeName,
            List<BiomeGroupData> allGroups,
            Map<String, Map<String, GroupOverrideStore.GroupPropertyOverride>> overrides,
            Map<String, PropertyDefinition> definitions) {

        List<BiomeGroupData> relevantGroups = allGroups.stream()
            .filter(g -> !g.isDeleted() && g.getBiomes().contains(biomeName))
            .collect(Collectors.toList());

        if (relevantGroups.isEmpty()) {
            return deepCopy(templateProperties);
        }

        List<PropertyValue> resolved = deepCopy(templateProperties);

        for (PropertyValue pv : resolved) {
            String propName = pv.getDefinition().name();
            String resolvedValue = resolveProperty(propName, pv.getValue(), relevantGroups, overrides, definitions);
            if (resolvedValue != null) {
                pv.setValue(resolvedValue);
            }
        }

        return resolved;
    }

    private static String resolveProperty(
            String propName,
            String templateValue,
            List<BiomeGroupData> relevantGroups,
            Map<String, Map<String, GroupOverrideStore.GroupPropertyOverride>> overrides,
            Map<String, PropertyDefinition> definitions) {

        boolean anyOverride = false;
        for (BiomeGroupData group : relevantGroups) {
            var groupOverrides = overrides.get(group.getName());
            if (groupOverrides == null) continue;
            var prop = groupOverrides.get(propName);
            if (prop != null && prop.override()) {
                anyOverride = true;
                break;
            }
        }
        if (!anyOverride) return null;

        BiomeGroupData opvGroup = null;
        for (BiomeGroupData group : relevantGroups) {
            var groupOverrides = overrides.get(group.getName());
            if (groupOverrides == null) continue;
            var prop = groupOverrides.get(propName);
            if (prop != null && prop.override() && prop.opv()) {
                opvGroup = group;
            }
        }

        if (opvGroup != null) {
            var prop = overrides.get(opvGroup.getName()).get(propName);
            return prop.value();
        }

        PropertyDefinition def = definitions.get(propName);
        boolean isList = def != null && def.type() == PropertyType.STRING_LIST;

        if (isList) {
            String accumulated = templateValue;
            for (BiomeGroupData group : relevantGroups) {
                var groupOverrides = overrides.get(group.getName());
                if (groupOverrides == null) continue;
                var prop = groupOverrides.get(propName);
                if (prop == null || !prop.override()) continue;
                if (prop.merge()) {
                    accumulated = accumulated + ", " + prop.value();
                } else {
                    accumulated = prop.value();
                }
            }
            return accumulated;
        }

        String result = templateValue;
        for (BiomeGroupData group : relevantGroups) {
            var groupOverrides = overrides.get(group.getName());
            if (groupOverrides == null) continue;
            var prop = groupOverrides.get(propName);
            if (prop == null || !prop.override()) continue;
            if (prop.value() != null) {
                result = prop.value();
            }
        }
        return result;
    }

    private static List<PropertyValue> deepCopy(List<PropertyValue> properties) {
        List<PropertyValue> copy = new ArrayList<>(properties.size());
        for (PropertyValue pv : properties) {
            copy.add(new PropertyValue(pv.getDefinition(), pv.getValue()));
        }
        return copy;
    }
}
