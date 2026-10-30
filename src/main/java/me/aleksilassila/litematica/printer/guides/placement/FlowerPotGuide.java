package me.aleksilassila.litematica.printer.guides.placement;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class FlowerPotGuide extends GeneralPlacementGuide {
    public FlowerPotGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return Collections.singletonList(new ItemStack(Items.FLOWER_POT));
    }

    /** An empty pot; FlowerPotFillGuide puts the plant in. */
    @Override
    protected boolean isRightResult(BlockState result) {
        return result.is(Blocks.FLOWER_POT);
    }
}
