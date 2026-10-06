package com.myworld3d;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/** Validated, checksummed saves. Version 1/2 files remain readable. */
public final class SaveSystem {
    private static final int MAGIC = 0x4D573344;
    private static final int VERSION = 3;
    private SaveSystem() {}

    public static final class Snapshot {
        public World world;
        public double x, y, z, yaw, pitch;
        public int health, toolTier, selected;
        public boolean flying, rainy, secretFound;
        public double timeOfDay;
        public int[] counts;
    }

    public static Path backupPath(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }

    /** Protect a user's existing world before starting a different one. */
    public static Path archiveBeforeNewWorld(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        Path copy = Files.createTempFile(file.toAbsolutePath().getParent(), "world-before-new-", ".mw3d");
        try { return Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { Files.deleteIfExists(copy); throw e; }
    }

    public static synchronized void save(Path file, World world, Player player, Inventory inventory,
                                          double timeOfDay, boolean rainy, boolean secretFound) throws IOException {
        Snapshot s = new Snapshot();
        s.world=world; s.x=player.x; s.y=player.y; s.z=player.z; s.yaw=player.yaw; s.pitch=player.pitch;
        s.health=player.health; s.toolTier=player.toolTier; s.flying=player.flying;
        s.timeOfDay=timeOfDay; s.rainy=rainy; s.secretFound=secretFound;
        s.selected=inventory.selected; s.counts=inventory.copyCounts();
        validate(s);
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), "world-write-", ".tmp");
        try {
            CRC32 crc = new CRC32();
            try (DataOutputStream out = new DataOutputStream(new CheckedOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)), crc))) {
                out.writeInt(MAGIC); out.writeInt(VERSION);
                out.writeLong(world.seed); out.writeInt(world.width); out.writeInt(world.height); out.writeInt(world.depth);
                byte[] blocks=world.copyBlocks();
                out.writeInt(blocks.length); out.write(blocks);
                out.writeDouble(s.x); out.writeDouble(s.y); out.writeDouble(s.z);
                out.writeDouble(s.yaw); out.writeDouble(s.pitch);
                out.writeInt(s.health); out.writeInt(s.toolTier); out.writeBoolean(s.flying);
                out.writeDouble(s.timeOfDay); out.writeBoolean(s.rainy); out.writeBoolean(s.secretFound);
                out.writeInt(s.selected); out.writeInt(s.counts.length);
                for (int count : s.counts) out.writeInt(count);
                out.writeLong(crc.getValue());
            }
            if (Files.exists(absolute)) {
                load(absolute);
                Files.copy(absolute, backupPath(absolute), StandardCopyOption.REPLACE_EXISTING);
            }
            try { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public static Snapshot load(Path file) throws IOException {
        if (Files.size(file) > 256L*128*256 + 4096) throw new IOException("存档文件过大");
        CRC32 crc = new CRC32();
        try (DataInputStream in = new DataInputStream(new CheckedInputStream(
                new BufferedInputStream(Files.newInputStream(file)), crc))) {
            if (in.readInt()!=MAGIC) throw new IOException("不是方块奇境存档");
            int version=in.readInt();
            if (version<1 || version>VERSION) throw new IOException("不支持的存档版本: "+version);
            long seed=in.readLong(); int width=in.readInt(), height=in.readInt(), depth=in.readInt();
            if (width<8 || height<8 || depth<8 || width>256 || height>128 || depth>256)
                throw new IOException("存档世界尺寸异常");
            int length=in.readInt();
            if (length!=width*height*depth) throw new IOException("存档方块数量异常");
            byte[] blocks=in.readNBytes(length);
            if (blocks.length!=length) throw new IOException("存档意外结束");
            for (byte id : blocks) if (!Block.validId(id & 255)) throw new IOException("存档包含未知方块");
            Snapshot s=new Snapshot();
            s.world=new World(width,height,depth,seed,false); s.world.replaceBlocks(blocks);
            s.x=in.readDouble(); s.y=in.readDouble(); s.z=in.readDouble();
            s.yaw=in.readDouble(); s.pitch=in.readDouble();
            s.health=in.readInt(); s.toolTier=in.readInt(); s.flying=in.readBoolean();
            s.timeOfDay=in.readDouble(); s.rainy=in.readBoolean(); s.secretFound=in.readBoolean();
            s.selected=in.readInt(); int countLength=in.readInt();
            if (countLength!=Block.values().length) throw new IOException("背包大小异常");
            s.counts=new int[countLength]; for(int i=0;i<countLength;i++)s.counts[i]=in.readInt();
            if(version>=3) {
                long actual=crc.getValue(), expected=in.readLong();
                if(actual!=expected)throw new IOException("存档校验失败");
            }
            if(in.read()!=-1)throw new IOException("存档存在多余数据");
            validate(s);
            return s;
        }
    }

    private static void validate(Snapshot s) throws IOException {
        if(!Double.isFinite(s.x)||!Double.isFinite(s.y)||!Double.isFinite(s.z)
                ||!Double.isFinite(s.yaw)||!Double.isFinite(s.pitch)||!Double.isFinite(s.timeOfDay))
            throw new IOException("存档包含无效数值");
        if(s.x<0||s.x>=s.world.width||s.z<0||s.z>=s.world.depth||s.y< -4||s.y>s.world.height+64
                ||Math.abs(s.pitch)>1.49||s.timeOfDay<0||s.timeOfDay>=1)
            throw new IOException("存档坐标或时间越界");
        if(s.health<0||s.health>20||s.toolTier<1||s.toolTier>3
                ||s.selected<0||s.selected>=Block.BUILD_PALETTE.length)
            throw new IOException("存档玩家状态异常");
        for(int count:s.counts)if(count<0||count>Inventory.MAX_COUNT)throw new IOException("存档物品数量异常");
        if(s.counts[Block.AIR.id]!=0||s.counts[Block.WATER.id]!=0)throw new IOException("存档包含不可携带物品");
    }
}
