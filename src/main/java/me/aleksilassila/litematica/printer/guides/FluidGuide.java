package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Pours water and lava sources with a bucket. Litematica names a bucket as the item for every fluid block, flowing
 * ones too, so the placement guides used to "place" fluids by emptying the bucket wherever the player happened to
 * look. Flowing fluid is left to its sources, and a source only goes in once the blocks around it stand, so it
 * cannot run into the unfinished build.
 */
public class FluidGuide extends BucketGuide {
    public FluidGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    public boolean skipOtherGuides() {
        return true;
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        FluidState wanted = targetState.getFluidState();
        if (!wanted.isSource() || currentState.getFluidState().isSourceOfType(wanted.getType()) || waitingForServer()) {
            return false;
        }
        // the printer does not break blocks: whatever stands there has to be removed by the player
        if (!currentState.getShape(state.world, state.blockPos).isEmpty()) {
            return false;
        }
        if (!playerHasRightItem(player) || !FluidRules.isContained(state, wanted.getType())) {
            return false;
        }

        aim = FluidRules.aimNextTo(player, state, wanted.getType());
        return aim != null;
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return List.of(new ItemStack(targetState.getFluidState().getType().getBucket()));
    }
}
