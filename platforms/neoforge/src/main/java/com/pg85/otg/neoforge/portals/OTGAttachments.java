package com.pg85.otg.neoforge.portals;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class OTGAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "otg");

    // Transient (not serialized) — portal state resets on relog, same as CCA behavior
    public static final Supplier<AttachmentType<OTGPlayerData>> OTG_PLAYER =
            ATTACHMENT_TYPES.register("otg_player",
                    () -> AttachmentType.builder(OTGPlayerData::new).build());
}
