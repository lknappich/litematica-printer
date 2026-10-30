package me.aleksilassila.litematica.printer.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Positions whose block the client predicted and the server has not confirmed yet. */
@Mixin(BlockStatePredictionHandler.class)
public interface BlockStatePredictionHandlerAccessor {
    @Accessor("serverVerifiedStates")
    Long2ObjectOpenHashMap<?> printer$getServerVerifiedStates();
}
