package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Fills a block that stands in water in the schematic with a water bucket, or scoops the water out of one that
 * should be dry. A block placed into air comes out dry and one placed into a water source comes out wet, whatever
 * the schematic says, and the other guides treat both as done.
 */
public class WaterlogGuide extends BucketGuide {
    public WaterlogGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!targetState.hasProperty(BlockStateProperties.WATERLOGGED)
                || !statesEqualIgnoreProperties(targetState, currentState, BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        boolean wet = targetState.getValue(BlockStateProperties.WATERLOGGED);
        if (currentState.getValue(BlockStateProperties.WATERLOGGED) == wet || waitingForServer() || !playerHasRightItem(player)) {
            return false;
        }
        if (wet && !FluidRules.isContained(state, Fluids.WATER)) {
            return false;
        }

        aim = FluidRules.aimAt(player, state.world, state.blockPos,
                wet ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY);
        return aim != null;
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        if (!targetState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return List.of();
        }
        return List.of(new ItemStack(targetState.getValue(BlockStateProperties.WATERLOGGED) ? Items.WATER_BUCKET : Items.BUCKET));
    }
}
