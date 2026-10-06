package com.myworld3d;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Versioned binary saves with a temp-file replace to avoid half-written worlds. */
public final class SaveSystem {
    private static final int MAGIC = 0x4D573344; // MW3D
    private static final int VERSION = 2;
    private SaveSystem() {}

    public static final class Snapshot {
        public World world;
        public double x, y, z, yaw, pitch;
        public int health, toolTier, selected;
        public boolean flying, rainy, secretFound;
        public double timeOfDay;
        public int[] counts;
    }

    public static void save(Path file, World world, Player player, Inventory inventory,
                            double timeOfDay, boolean rainy, boolean secretFound) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(MAGIC); out.writeInt(VERSION);
            out.writeLong(world.seed); out.writeInt(world.width); out.writeInt(world.height); out.writeInt(world.depth);
            byte[] blocks = world.copyBlocks();
            out.writeInt(blocks.length); out.write(blocks);
            out.writeDouble(player.x); out.writeDouble(player.y); out.writeDouble(player.z);
            out.writeDouble(player.yaw); out.writeDouble(player.pitch);
            out.writeInt(player.health); out.writeInt(player.toolTier); out.writeBoolean(player.flying);
            out.writeDouble(timeOfDay); out.writeBoolean(rainy); out.writeBoolean(secretFound);
            out.writeInt(inventory.selected);
            int[] counts = inventory.copyCounts();
            out.writeInt(counts.length); for (int count : counts) out.writeInt(count);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Snapshot load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("不是方块奇境存档");
            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("不支持的存档版本: " + version);
            long seed = in.readLong(); int width = in.readInt(), height = in.readInt(), depth = in.readInt();
            if (width > 256 || height > 128 || depth > 256) throw new IOException("存档世界尺寸异常");
            World world = new World(width, height, depth, seed, false);
            int length = in.readInt();
            if (length != width * height * depth) throw new IOException("存档方块数据损坏");
            byte[] blocks = in.readNBytes(length);
            if (blocks.length != length) throw new IOException("存档意外结束");
            world.replaceBlocks(blocks);
            Snapshot s = new Snapshot(); s.world = world;
            s.x = in.readDouble(); s.y = in.readDouble(); s.z = in.readDouble();
            s.yaw = in.readDouble(); s.pitch = in.readDouble();
            s.health = in.readInt(); s.toolTier = in.readInt(); s.flying = in.readBoolean();
            s.timeOfDay = in.readDouble(); s.rainy = in.readBoolean(); s.secretFound = in.readBoolean();
            s.selected = in.readInt();
            int countLength = in.readInt();
            if (countLength < 0 || countLength > 256) throw new IOException("背包数据损坏");
            s.counts = new int[countLength]; for (int i = 0; i < countLength; i++) s.counts[i] = in.readInt();
            return s;
        }
    }
}
