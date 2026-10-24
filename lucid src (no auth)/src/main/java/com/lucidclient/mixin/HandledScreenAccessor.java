package com.lucidclient.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin @Accessor for HandledScreen.focusedSlot.
 *
 * HandledScreen.focusedSlot is PROTECTED — accessible via reflection in dev,
 * but production Fabric maps the field to intermediary names so plain
 * reflection fails. This accessor generates a public getter the Lucid
 * modules (HoverTotem) can use safely in production.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("focusedSlot")
    Slot lucid$getFocusedSlot();
}
