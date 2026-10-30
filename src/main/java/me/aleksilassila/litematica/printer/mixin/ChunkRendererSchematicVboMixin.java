package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.util.OverlayType;
import me.aleksilassila.litematica.printer.guides.Guide;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks from anyStateBlocks get no "wrong state" overlay once the block stands. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public class ChunkRendererSchematicVboMixin {
    @Inject(method = "getOverlayType", at = @At("HEAD"), cancellable = true)
    private void printer$acceptAnyState(BlockState schematic, BlockState client, CallbackInfoReturnable<OverlayType> cir) {
        if (schematic.getBlock() == client.getBlock() && Guide.acceptsAnyState(schematic.getBlock())) {
            cir.setReturnValue(OverlayType.NONE);
        }
    }
}
