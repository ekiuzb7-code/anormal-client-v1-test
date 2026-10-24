package com.anormal.client.mixin;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for the package-private inner
 * {@code ChunkData.BlockEntityData} class — exposes its private final fields
 * so the SusChunkESP packet-interception mixin can decode the packed position
 * and read the leaked NBT without referencing the package-private class
 * directly (we cast each list element to this accessor interface).
 *
 * Fields:
 *   - {@code localXz} : int  (packed local X/Z, 4 bits each —
 *                            x = localXz & 15, z = (localXz >> 4) & 15)
 *   - {@code y}       : int  (world Y of the block entity)
 *   - {@code type}    : BlockEntityType
 *   - {@code nbt}     : NbtCompound (the leaked NBT data)
 *
 * The @Mixin target uses the runtime class reference via the outer class's
 * declared inner class — Mixin resolves {@code ChunkData$BlockEntityData}
 * at the bytecode level even though Java source can't name it.
 */
@Mixin(targets = "net.minecraft.network.packet.s2c.play.ChunkData$BlockEntityData")
public interface BlockEntityDataAccessor {
    @Accessor("localXz")
    int anormal$getLocalXz();
    @Accessor("y")
    int anormal$getY();
    @Accessor("type")
    BlockEntityType<?> anormal$getType();
    @Accessor("nbt")
    NbtCompound anormal$getNbt();
}