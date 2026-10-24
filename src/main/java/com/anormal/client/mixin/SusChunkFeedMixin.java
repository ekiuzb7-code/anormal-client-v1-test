package com.anormal.client.mixin;

import com.anormal.client.lucid.SusChunkCache;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Feeds {@link SusChunkCache} from the packets the server sends.
 *
 * <p>Base-finding works because servers leak information the player should not have: the full
 * block-entity list of every loaded chunk (so chests, shulkers and barrels far away are visible to
 * the client even through walls), single-tile block-entity updates, and sound positions that
 * betray farms and redstone. Lucid's heuristics turn those leaks into a score per chunk.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public class SusChunkFeedMixin {

    /**
     * Initial chunk load: the server sends the full block-entity list here. The list's element
     * type is package-private, so entries are read through {@link BlockEntityDataAccessor}.
     */
    @Inject(method = "onChunkData", at = @At("HEAD"))
    private void anormal$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        try {
            int cx = packet.getChunkX();
            int cz = packet.getChunkZ();
            var data = packet.getChunkData();
            if (data == null) return;

            SusChunkCache.get().setLoaded(cx, cz, true);

            List<?> list = ((ChunkDataAccessor) (Object) data).anormal$getBlockEntities();
            if (list == null) return;

            for (Object bed : list) {
                if (bed == null) continue;
                BlockEntityDataAccessor a = (BlockEntityDataAccessor) bed;
                BlockEntityType<?> type = a.anormal$getType();
                if (type == null) continue;
                if (!SusChunkCache.isContainer(type)) continue;

                int localXz = a.anormal$getLocalXz();
                int lx = localXz & 15;
                int lz = (localXz >> 4) & 15;
                int y = a.anormal$getY();
                BlockPos pos = new BlockPos(cx * 16 + lx, y, cz * 16 + lz);
                var nbt = a.anormal$getNbt();
                if (nbt == null) nbt = new net.minecraft.nbt.NbtCompound();
                SusChunkCache.get().recordFromChunkData(cx, cz, pos, type, nbt);
            }
        } catch (Throwable ignored) {
            // Never let a packet-side parse failure crash the network thread.
        }
    }

    /** Single-tile block-entity update (chest opened, hopper changed, spawner activated...). */
    @Inject(method = "onBlockEntityUpdate", at = @At("HEAD"))
    private void anormal$onBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            BlockPos pos = packet.getPos();
            if (pos == null) return;
            BlockEntityType<?> type = packet.getBlockEntityType();
            if (type == null) return;
            if (!SusChunkCache.isContainer(type)) return;
            var nbt = packet.getNbt();
            if (nbt == null) return;
            SusChunkCache.get().recordFromBlockEntityUpdate(pos, type, nbt);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Chunk unload: mark it as not loaded so the cache can age it out. The cached data is kept on
     * purpose - the whole point is that the leak persists after walking away.
     */
    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void anormal$onUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        try {
            ChunkPos pos = packet.pos();
            SusChunkCache.get().setLoaded(pos.x, pos.z, false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sound triangulation: the server must send sound coordinates so the client can pan audio.
     * Mechanical sounds (pistons, note blocks, anvils, hoppers...) are the strongest base signal;
     * hostile mob damage flags farms.
     */
    @Inject(method = "onPlaySound", at = @At("HEAD"))
    private void anormal$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            int bx = (int) Math.floor(packet.getX());
            int by = (int) Math.floor(packet.getY());
            int bz = (int) Math.floor(packet.getZ());
            int cx = bx >> 4;
            int cz = bz >> 4;
            boolean mechanical = isMechanicalSound(packet.getSound(), packet.getCategory(), by);
            SusChunkCache.get().recordSoundEvent(cx, cz, mechanical);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMechanicalSound(RegistryEntry<SoundEvent> sound, SoundCategory cat, int by) {
        if (cat == SoundCategory.BLOCKS) return true;
        if (cat == SoundCategory.HOSTILE) return true;
        if (sound == null || !sound.hasKeyAndValue()) return false;
        SoundEvent se = sound.value();
        if (sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_HARP)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BASS)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BELL)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_CHIME)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_GUITAR)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BIT)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BANJO)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_PLING)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_SNARE)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_HAT)
                || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM)) {
            return true;
        }
        return se == SoundEvents.BLOCK_PISTON_EXTEND
                || se == SoundEvents.BLOCK_PISTON_CONTRACT
                || se == SoundEvents.BLOCK_ANVIL_HIT
                || se == SoundEvents.BLOCK_ANVIL_LAND
                || se == SoundEvents.BLOCK_ANVIL_USE
                || se == SoundEvents.BLOCK_GRINDSTONE_USE
                || se == SoundEvents.BLOCK_SMOKER_SMOKE
                || se == SoundEvents.BLOCK_CAMPFIRE_CRACKLE
                || se == SoundEvents.BLOCK_COMPOSTER_READY
                || se == SoundEvents.BLOCK_LANTERN_STEP
                || se == SoundEvents.BLOCK_LANTERN_HIT
                || se == SoundEvents.BLOCK_BREWING_STAND_BREW
                || se == SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT
                || se == SoundEvents.BLOCK_LADDER_STEP
                || se == SoundEvents.BLOCK_METAL_HIT
                || se == SoundEvents.BLOCK_METAL_PLACE
                || se == SoundEvents.BLOCK_METAL_STEP;
    }
}
