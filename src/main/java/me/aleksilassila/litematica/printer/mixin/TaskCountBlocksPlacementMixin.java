package me.aleksilassila.litematica.printer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement;
import me.aleksilassila.litematica.printer.guides.Guide;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The material list does not count blocks from anyStateBlocks as missing when the block stands in another state,
 * as if "ignore state" were on just for them.
 */
@Mixin(value = TaskCountBlocksPlacement.class, remap = false)
public class TaskCountBlocksPlacementMixin {
    @ModifyExpressionValue(method = "countAtPosition", at = @At(value = "FIELD",
            target = "Lfi/dy/masa/litematica/scheduler/tasks/TaskCountBlocksPlacement;ignoreState:Z"))
    private boolean printer$acceptAnyState(boolean ignoreState, @Local(ordinal = 0) BlockState schematic) {
        return ignoreState || Guide.acceptsAnyState(schematic.getBlock());
    }
}
