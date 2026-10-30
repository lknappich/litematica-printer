package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.BlockHelperImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

abstract public class Guide extends BlockHelperImpl {
    /** The parsed anyStateBlocks list; Litematica's chunk builder threads read it too. */
    private record AnyState(List<String> names, Set<Block> blocks) {
    }

    private static volatile AnyState anyState = new AnyState(List.of(), Set.of());

    protected final SchematicBlockState state;
    protected final BlockState currentState;
    protected final BlockState targetState;

    public Guide(SchematicBlockState state) {
        this.state = state;
        this.currentState = state.currentState;
        this.targetState = state.targetState;
    }

    protected boolean playerHasRightItem(LocalPlayer player) {
        return getRequiredItemStackSlot(player) != -1;
    }

    protected int getSlotWithItem(LocalPlayer player, ItemStack itemStack) {
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getNonEquipmentItems().size(); ++i) {
            if (itemStack.isEmpty() && inventory.getNonEquipmentItems().get(i).is(itemStack.getItem())) {
                return i;
            }
            if (!inventory.getNonEquipmentItems().get(i).isEmpty() && ItemStack.isSameItem(inventory.getNonEquipmentItems().get(i), itemStack)) {
                return i;
            }
        }

        return -1;
    }

    protected int getRequiredItemStackSlot(LocalPlayer player) {
        if (player.getAbilities().instabuild) {
            return player.getInventory().getSelectedSlot();
        }

        Optional<ItemStack> requiredItem = getRequiredItem(player);
        return requiredItem.map(itemStack -> getSlotWithItem(player, itemStack)).orElse(-1);
    }

    public boolean canExecute(LocalPlayer player) {
        if (!playerHasRightItem(player)) {
            return false;
        }

        BlockState targetState = state.targetState;
        BlockState currentState = state.currentState;

        return !statesEqual(targetState, currentState);
    }

    abstract public @Nonnull List<Action> execute(LocalPlayer player);

    abstract protected @Nonnull List<ItemStack> getRequiredItems();

    /**
     * Returns the first required item that the player has access to,
     * or empty if the items are inaccessible.
     */
    protected Optional<ItemStack> getRequiredItem(LocalPlayer player) {
        List<ItemStack> requiredItems = getRequiredItems();

        for (ItemStack requiredItem : requiredItems) {
            if (player.getAbilities().instabuild) {
                return Optional.of(requiredItem);
            }

            int slot = getSlotWithItem(player, requiredItem);
            if (slot > -1) {
                return Optional.of(requiredItem);
            }
        }

        return Optional.empty();
    }

    protected boolean statesEqualIgnoreProperties(BlockState state1, BlockState state2,
                                                  Property<?>... propertiesToIgnore) {
        if (state1.getBlock() != state2.getBlock()) {
            return false;
        }
        if (acceptsAnyState(state1.getBlock())) {
            return true;
        }

        loop:
        for (Property<?> property : state1.getProperties()) {
            if (property == BlockStateProperties.WATERLOGGED && !(state1.getBlock() instanceof CoralPlantBlock)) {
                continue;
            }

            for (Property<?> ignoredProperty : propertiesToIgnore) {
                if (property == ignoredProperty) {
                    continue loop;
                }
            }

            try {
                if (!state1.getValue(property).equals(state2.getValue(property))) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Blocks from the anyStateBlocks list count as printed once the block stands, whatever its state (the note of a
     * note block, say), so the printer neither picks a placement for the state nor clicks them to change it, and
     * Litematica neither marks them nor counts them as missing.
     */
    public static boolean acceptsAnyState(Block block) {
        AnyState current = anyState;
        try {
            List<String> names = Configs.ANY_STATE_BLOCKS.getStrings();
            if (!names.equals(current.names())) {
                Set<Block> blocks = new HashSet<>();
                for (String name : names) {
                    Identifier id = Identifier.tryParse(name.trim());
                    if (id != null) {
                        BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
                    }
                }
                current = new AnyState(new ArrayList<>(names), blocks);
                anyState = current;
            }
        } catch (RuntimeException e) {
            // the list is being edited in the config screen right now; the last copy does until the next call
        }
        return current.blocks().contains(block);
    }

    protected static <T extends Comparable<T>> Optional<T> getProperty(BlockState blockState, Property<T> property) {
        if (blockState.hasProperty(property)) {
            return Optional.of(blockState.getValue(property));
        }
        return Optional.empty();
    }

    /**
     * Returns true if
     */
    protected boolean statesEqual(BlockState state1, BlockState state2) {
        return statesEqualIgnoreProperties(state1, state2);
    }

    public boolean skipOtherGuides() {
        return false;
    }
}
