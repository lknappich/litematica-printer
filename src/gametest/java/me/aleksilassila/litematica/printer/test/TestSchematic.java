package me.aleksilassila.litematica.printer.test;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes a small .litematic with one region and places it with Litematica. */
final class TestSchematic {
    private final String name;
    private final int sizeX, sizeY, sizeZ;
    private final CompoundTag[] blocks;

    TestSchematic(String name, int sizeX, int sizeY, int sizeZ) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new CompoundTag[sizeX * sizeY * sizeZ];
    }

    /** A block state, e.g. block("minecraft:oak_slab", "type=bottom", "waterlogged=true"). */
    static CompoundTag block(String id, String... properties) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", id);
        if (properties.length > 0) {
            CompoundTag props = new CompoundTag();
            for (String p : properties) {
                String[] kv = p.split("=");
                props.putString(kv[0], kv[1]);
            }
            state.put("Properties", props);
        }
        return state;
    }

    TestSchematic set(int x, int y, int z, CompoundTag state) {
        blocks[index(x, y, z)] = state;
        return this;
    }

    /** Writes the file, places it at origin and selects the placement. */
    void place(Path dir, BlockPos origin) throws IOException {
        String fileName = name.toLowerCase(Locale.ROOT).replace(' ', '_') + ".litematic";
        Files.createDirectories(dir);
        NbtIo.writeCompressed(tag(), dir.resolve(fileName));

        LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, fileName);
        if (schematic == null) {
            throw new IllegalStateException("Litematica could not read the test schematic");
        }
        SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        manager.addSchematicPlacement(placement, false);
        manager.setSelectedSchematicPlacement(placement);
    }

    /** Litematica keeps placements by world name, so a test world can start with the last test's placements. */
    static void removeAllPlacements() {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        for (SchematicPlacement placement : new ArrayList<>(manager.getAllSchematicsPlacements())) {
            manager.removeSchematicPlacement(placement);
        }
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    private CompoundTag tag() {
        CompoundTag air = block("minecraft:air");
        ListTag palette = new ListTag();
        List<String> keys = new ArrayList<>();
        palette.add(air);
        keys.add(air.toString());
        int[] ids = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            CompoundTag state = blocks[i] == null ? air : blocks[i];
            String key = state.toString();
            if (!keys.contains(key)) {
                keys.add(key);
                palette.add(state);
            }
            ids[i] = keys.indexOf(key);
        }
        // Litematica packs the palette ids tightly, at least 2 bits each, entries may span two longs
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(keys.size() - 1));
        long[] packed = new long[(blocks.length * bits + 63) / 64];
        for (int i = 0; i < blocks.length; i++) {
            int bit = i * bits;
            packed[bit >> 6] |= (long) ids[i] << (bit & 63);
            if ((bit & 63) + bits > 64) {
                packed[(bit >> 6) + 1] |= (long) ids[i] >>> (64 - (bit & 63));
            }
        }

        CompoundTag size = xyz(sizeX, sizeY, sizeZ);
        CompoundTag region = new CompoundTag();
        region.put("Position", xyz(0, 0, 0));
        region.put("Size", size);
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", packed);
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());
        CompoundTag regions = new CompoundTag();
        regions.put("Main", region);

        long now = System.currentTimeMillis();
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", name);
        meta.putString("Author", "test");
        meta.putString("Description", "");
        meta.putInt("RegionCount", 1);
        meta.putLong("TotalBlocks", blocks.length);
        meta.putLong("TotalVolume", blocks.length);
        meta.put("EnclosingSize", size.copy());
        meta.putLong("TimeCreated", now);
        meta.putLong("TimeModified", now);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 7);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        root.put("Metadata", meta);
        root.put("Regions", regions);
        return root;
    }

    private static CompoundTag xyz(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }
}
