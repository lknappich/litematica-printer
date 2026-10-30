package me.aleksilassila.litematica.printer.guides.placement;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import me.aleksilassila.litematica.printer.BlockHelper;
import me.aleksilassila.litematica.printer.SchematicBlockState;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class FarmlandGuide extends GeneralPlacementGuide
{
	public FarmlandGuide(SchematicBlockState state)
	{
		super(state);
	}

	@Override
	protected @Nonnull List<ItemStack> getRequiredItems()
	{
		return Arrays.stream(BlockHelper.TILLABLE_BLOCKS).map(b -> getBlockItem(b.defaultBlockState())).toList();
	}

	/** Dirt or the like; TillingGuide turns it into farmland. */
	@Override
	protected boolean isRightResult(BlockState result)
	{
		return Arrays.asList(BlockHelper.TILLABLE_BLOCKS).contains(result.getBlock());
	}
}
