package com.anormal.client.mixin;

import net.minecraft.network.packet.s2c.play.ChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin exposing {@link ChunkData}'s private
 * {@code blockEntities} list (a {@code List<BlockEntityData>}) so the
 * SusChunkESP packet-interception mixin can read the raw block-entity
 * records the server leaked, before the vanilla engine even processes them.
 *
 * The inner {@code BlockEntityData} class is package-private in 1.21.11, so
 * we type the list as {@code List<?>} and downcast each element to
 * {@link BlockEntityDataAccessor} to read its fields. This avoids any direct
 * reference to the package-private inner class.
 */
@Mixin(ChunkData.class)
public interface ChunkDataAccessor {
    @Accessor("blockEntities")
    List<?> anormal$getBlockEntities();
}