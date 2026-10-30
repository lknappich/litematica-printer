package me.aleksilassila.litematica.printer.guides.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.actions.ReleaseShiftAction;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guides.FluidRules;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import me.aleksilassila.litematica.printer.implementation.actions.InteractActionImpl;
import me.aleksilassila.litematica.printer.mixin.BlockItemInvoker;
import me.aleksilassila.litematica.printer.mixin.BlockStatePredictionHandlerAccessor;
import me.aleksilassila.litematica.printer.mixin.ClientLevelAccessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import fi.dy.masa.litematica.util.ItemUtils;

/**
 * Guide that clicks its neighbors to create a placement in target position.
 */
abstract public class PlacementGuide extends Guide
{
	public PlacementGuide(SchematicBlockState state)
	{
		super(state);
	}

	protected ItemStack getBlockItem(BlockState state)
	{
		// Let's use the Litematica Pick Block Cache for this.
		return ItemUtils.getItemForBlock(this.state.world, this.state.blockPos, state, true);
	}

	protected Optional<Block> getRequiredItemAsBlock(LocalPlayer player)
	{
		Optional<ItemStack> requiredItem = getRequiredItem(player);

		if (requiredItem.isEmpty())
		{
			return Optional.empty();
		}
		else
		{
			ItemStack itemStack = requiredItem.get();

            if (itemStack.getItem() instanceof BlockItem)
            {
                return Optional.of(((BlockItem) itemStack.getItem()).getBlock());
            }
            else
            {
                return Optional.empty();
            }
		}
	}

	@Override
	protected @Nonnull List<ItemStack> getRequiredItems()
	{
		Printer.printDebug("PlacementGuide#getRequiredItems() - target state [{}]", state.targetState.toString());
		return Collections.singletonList(getBlockItem(state.targetState));
	}

	abstract protected boolean getUseShift(SchematicBlockState state);

	@Nullable
	abstract public PrinterPlacementContext getPlacementContext(LocalPlayer player);

	@Override
	public boolean canExecute(LocalPlayer player)
	{
        if (!super.canExecute(player))
        {
            return false;
        }

		List<ItemStack> requiredItems = getRequiredItems();
        if (requiredItems.isEmpty() || requiredItems.stream().allMatch(i -> i.is(Items.AIR)))
        {
            return false;
        }

		PrinterPlacementContext ctx = getPlacementContext(player);
        if (ctx == null || !ctx.canPlace())
        {
            return false;
        }
//        if (!state.currentState.getMaterial().isReplaceable()) return false;
        if (!Configs.REPLACE_FLUIDS_SOURCE_BLOCKS.getBooleanValue()
                && getProperty(state.currentState, LiquidBlock.LEVEL).orElse(1) == 0)
        {
            return false;
        }

		BlockState resultState = getRequiredItemAsBlock(player)
				.orElse(targetState.getBlock())
				.getStateForPlacement(ctx);

		if (resultState != null)
		{
            if (!resultState.canSurvive(state.world, state.blockPos))
            {
                return false;
            }
            // wait until the blocks that hold the water back stand, or the water would wash it away again
            if (FluidRules.washesAway(resultState) && FluidRules.strayFluidNear(state))
            {
                return false;
            }
            if (!placesRightBlock(player, ctx))
            {
                return false;
            }
			return !(currentState.getBlock() instanceof LiquidBlock) || canPlaceInWater(resultState);
		}
		else
		{
			return false;
		}
	}

	/**
	 * Whether placing gives what the schematic wants there, or a first step that another guide finishes (a log that
	 * gets stripped, a slab that becomes a double slab, ...). Anything else would leave a wrong block that the printer
	 * cannot remove again.
	 */
	protected boolean isRightResult(BlockState result)
	{
		return statesEqual(result, targetState);
	}

	/**
	 * Places the click the way the server will: with the view the server gets (the printer's look direction only
	 * when rotate is on), through the item's own placement logic, and checks that the block lands on this spot and
	 * comes out right. A clicked neighbour that the client predicted but the server has not confirmed yet is not
	 * trusted: if the server never placed it, the new block would end up in its place.
	 */
	protected boolean placesRightBlock(LocalPlayer player, PrinterPlacementContext ctx)
	{
		BlockPos clicked = ctx.hitResult.getBlockPos();
		if (!clicked.equals(state.blockPos) && isUnconfirmed(state.world, clicked))
		{
			return false;
		}
		if (!(ctx.getItemInHand().getItem() instanceof BlockItem blockItem))
		{
			return ctx.getClickedPos().equals(state.blockPos);
		}

		PrinterPlacementContext seen = Configs.ROTATE.getBooleanValue() ? ctx
				: new PrinterPlacementContext(player, ctx.hitResult, ctx.getItemInHand(), ctx.requiredItemSlot,
				null, ctx.shouldSneak);
		BlockPlaceContext placed = blockItem.updatePlacementContext(seen);
		if (placed == null || !placed.getClickedPos().equals(state.blockPos))
		{
			return false;
		}
		BlockState result = ((BlockItemInvoker) blockItem).printer$getPlacementState(placed);
		Printer.printDebug("PlacementGuide#placesRightBlock() - {} gives {}", ctx, result);
		return result != null && isRightResult(result);
	}

	private static boolean isUnconfirmed(Level world, BlockPos pos)
	{
		if (!(world instanceof ClientLevel level))
		{
			return false;
		}
		BlockStatePredictionHandler predictions = ((ClientLevelAccessor) level).printer$getPredictionHandler();
		return ((BlockStatePredictionHandlerAccessor) predictions).printer$getServerVerifiedStates()
				.containsKey(pos.asLong());
	}

	@Override
	public @Nonnull List<Action> execute(LocalPlayer player)
	{
		List<Action> actions = new ArrayList<>();
		PrinterPlacementContext ctx = getPlacementContext(player);

        if (ctx == null)
        {
            return actions;
        }
		actions.add(new PrepareAction(ctx));
		actions.add(new InteractActionImpl(ctx));
        if (ctx.shouldSneak)
        {
            actions.add(new ReleaseShiftAction());
        }

		return actions;
	}

	protected static boolean canBeClicked(Level world, BlockPos pos)
	{
		return getOutlineShape(world, pos) != Shapes.empty()
				&& !(world.getBlockState(pos).getBlock() instanceof SignBlock); // FIXME signs
	}

	private static VoxelShape getOutlineShape(Level world, BlockPos pos)
	{
		return world.getBlockState(pos).getShape(world, pos);
	}

	public boolean isInteractive(Block block)
	{
		for (Class<?> clazz : interactiveBlocks)
		{
			if (clazz.isInstance(block))
			{
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("deprecation")
	private boolean canPlaceInWater(BlockState blockState)
	{
		Block block = blockState.getBlock();

		if (block instanceof LiquidBlockContainer)
		{
			return true;
		}
		else if (!(block instanceof DoorBlock) && !(blockState.getBlock() instanceof SignBlock)
				&& !blockState.is(Blocks.LADDER) && !blockState.is(Blocks.SUGAR_CANE)
				&& !blockState.is(Blocks.BUBBLE_COLUMN))
		{
//			Material material = blockState.getMaterial();
//			if (material != Material && material != Material.STRUCTURE_VOID && material != Material.UNDERWATER_PLANT && material != Material.REPLACEABLE_UNDERWATER_PLANT)
//			{
//				return material.blocksMovement();
//			}
//			else
//			{
//				return true;
//			}

            return block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && blockState.isSolid();
		}

		return true;
	}
}
