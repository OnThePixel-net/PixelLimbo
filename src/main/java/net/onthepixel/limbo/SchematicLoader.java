package net.onthepixel.limbo;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * SpongeSchematic loader (v2 + v3).
 * Reads a .schem file (gzip + NBT) and pastes the blocks into a Minestom
 * Instance at the given origin.
 *
 * Format reference: https://github.com/SpongePowered/Schematic-Specification
 */
public final class SchematicLoader {

    public record Loaded(int width, int height, int length, int[] offset) {}

    public static Loaded load(Path schemFile, Instance instance, Pos pasteOrigin) throws IOException {
        CompoundBinaryTag root = BinaryTagIO.unlimitedReader().read(schemFile, BinaryTagIO.Compression.GZIP);

        // v3 wraps everything in a "Schematic" subcompound, v2 puts it at root.
        CompoundBinaryTag schem = root.getCompound("Schematic", root);
        int version = schem.getInt("Version", 2);

        return switch (version) {
            case 2 -> loadV2(schem, instance, pasteOrigin);
            case 3 -> loadV3(schem, instance, pasteOrigin);
            default -> throw new IOException("Unsupported schematic version: " + version);
        };
    }

    private static Loaded loadV2(CompoundBinaryTag schem, Instance instance, Pos origin) {
        int width = schem.getShort("Width") & 0xFFFF;
        int height = schem.getShort("Height") & 0xFFFF;
        int length = schem.getShort("Length") & 0xFFFF;
        int[] offset = schem.getIntArray("Offset");
        if (offset == null || offset.length != 3) offset = new int[]{0, 0, 0};

        CompoundBinaryTag palette = schem.getCompound("Palette");
        byte[] blockData = schem.getByteArray("BlockData");

        Map<Integer, Block> idToBlock = buildPalette(palette);
        pasteBlocks(instance, origin, width, height, length, blockData, idToBlock);
        return new Loaded(width, height, length, offset);
    }

    private static Loaded loadV3(CompoundBinaryTag schem, Instance instance, Pos origin) {
        int width = schem.getShort("Width") & 0xFFFF;
        int height = schem.getShort("Height") & 0xFFFF;
        int length = schem.getShort("Length") & 0xFFFF;
        int[] offset = schem.getIntArray("Offset");
        if (offset == null || offset.length != 3) offset = new int[]{0, 0, 0};

        CompoundBinaryTag blocks = schem.getCompound("Blocks");
        CompoundBinaryTag palette = blocks.getCompound("Palette");
        byte[] blockData = blocks.getByteArray("Data");

        Map<Integer, Block> idToBlock = buildPalette(palette);
        pasteBlocks(instance, origin, width, height, length, blockData, idToBlock);
        return new Loaded(width, height, length, offset);
    }

    private static Map<Integer, Block> buildPalette(CompoundBinaryTag palette) {
        Map<Integer, Block> out = new HashMap<>();
        for (Map.Entry<String, ? extends BinaryTag> e : palette) {
            String key = e.getKey();
            int id = ((IntBinaryTag) e.getValue()).value();
            out.put(id, parseBlockState(key));
        }
        return out;
    }

    /**
     * Parse "minecraft:oak_stairs[facing=east,half=top]" into a Minestom Block
     * with the right state properties applied.
     */
    private static Block parseBlockState(String raw) {
        String name = raw;
        Map<String, String> props = Map.of();
        int bracket = raw.indexOf('[');
        if (bracket >= 0) {
            name = raw.substring(0, bracket);
            String inner = raw.substring(bracket + 1, raw.length() - 1);
            props = new HashMap<>();
            for (String pair : inner.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        Block block = Block.fromKey(name);
        if (block == null) {
            // Unknown block id (e.g. newer block name not in this Minestom version)
            return Block.AIR;
        }
        return props.isEmpty() ? block : block.withProperties(props);
    }

    private static void pasteBlocks(Instance instance, Pos origin,
                                    int width, int height, int length,
                                    byte[] data, Map<Integer, Block> palette) {
        int i = 0;
        int index = 0;
        int expected = width * height * length;
        while (i < data.length && index < expected) {
            // VarInt decode
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IllegalStateException("VarInt too large");
            } while ((b & 0x80) != 0);

            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            index++;

            Block block = palette.getOrDefault(value, Block.AIR);
            if (block == Block.AIR) continue;
            instance.setBlock(
                    origin.blockX() + x,
                    origin.blockY() + y,
                    origin.blockZ() + z,
                    block
            );
        }
    }
}
