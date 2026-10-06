package com.myworld3d;

import java.util.Arrays;
import java.util.Random;

/** Finite but generous voxel world with deterministic terrain generation. */
public final class World {
    public static final int DEFAULT_WIDTH = 112;
    public static final int DEFAULT_HEIGHT = 48;
    public static final int DEFAULT_DEPTH = 112;
    public static final int SEA_LEVEL = 16;

    public final int width;
    public final int height;
    public final int depth;
    public final long seed;
    public final int shrineX;
    public final int shrineZ;
    private final byte[] blocks;

    public World(long seed) { this(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_DEPTH, seed, true); }

    public World(int width, int height, int depth, long seed, boolean generate) {
        if (width < 8 || height < 8 || depth < 8) throw new IllegalArgumentException("世界尺寸太小");
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.seed = seed;
        this.blocks = new byte[width * height * depth];
        this.shrineX = Math.min(width - 9, width / 2 + 27);
        this.shrineZ = Math.min(depth - 9, depth / 2 + 20);
        if (generate) generate();
    }

    private int index(int x, int y, int z) { return (y * depth + z) * width + x; }
    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    public Block get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return y < 0 ? Block.STONE : Block.AIR;
        return Block.fromId(blocks[index(x, y, z)] & 0xFF);
    }

    public void set(int x, int y, int z, Block block) {
        if (inBounds(x, y, z)) blocks[index(x, y, z)] = (byte) block.id;
    }

    public byte[] copyBlocks() { return Arrays.copyOf(blocks, blocks.length); }

    public void replaceBlocks(byte[] data) {
        if (data.length != blocks.length) throw new IllegalArgumentException("存档世界尺寸不匹配");
        System.arraycopy(data, 0, blocks, 0, data.length);
    }

    public int surfaceY(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= depth) return 1;
        for (int y = height - 2; y >= 1; y--) {
            Block b = get(x, y, z);
            if (b.solid && b != Block.LEAVES && b != Block.WOOD) return y;
        }
        return 1;
    }

    private void generate() {
        Random random = new Random(seed);
        int[][] surface = new int[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double broad = Noise.fbm(x / 34.0, z / 34.0, seed, 4);
                double detail = Noise.fbm(x / 12.0, z / 12.0, seed + 47, 3);
                double islandEdge = Math.min(Math.min(x, width - 1 - x), Math.min(z, depth - 1 - z));
                int top = (int) Math.round(18 + broad * 6.0 + detail * 2.2);
                if (islandEdge < 4) top = Math.min(top, 13 + (int) islandEdge);
                top = clamp(top, 7, height - 10);
                surface[x][z] = top;
                for (int y = 0; y <= top; y++) {
                    Block block;
                    if (y == 0) block = Block.BRICKS;
                    else if (y == top) block = top <= SEA_LEVEL + 1 ? Block.SAND : Block.GRASS;
                    else if (y >= top - 3) block = top <= SEA_LEVEL + 1 ? Block.SAND : Block.DIRT;
                    else block = chooseRock(random, y, top);
                    set(x, y, z, block);
                }
                for (int y = top + 1; y <= SEA_LEVEL; y++) set(x, y, z, Block.WATER);
            }
        }

        carveCaves(random, surface);
        growNature(random, surface);
        buildShrine();
        clearSpawn();
    }

    private Block chooseRock(Random random, int y, int top) {
        double r = random.nextDouble();
        if (y < top - 5 && r < 0.018) return Block.COAL_ORE;
        if (y < 15 && r < 0.010) return Block.IRON_ORE;
        if (y < 10 && r < 0.0035) return Block.GOLD_ORE;
        return Block.STONE;
    }

    private void carveCaves(Random random, int[][] surface) {
        int walkers = Math.max(6, width * depth / 850);
        for (int i = 0; i < walkers; i++) {
            double x = 8 + random.nextInt(Math.max(1, width - 16));
            double z = 8 + random.nextInt(Math.max(1, depth - 16));
            double y = 5 + random.nextInt(Math.max(1, Math.min(10, height - 10)));
            double yaw = random.nextDouble() * Math.PI * 2.0;
            for (int step = 0; step < 75; step++) {
                int radius = random.nextDouble() < 0.12 ? 2 : 1;
                carveSphere((int) x, (int) y, (int) z, radius, surface);
                yaw += (random.nextDouble() - 0.5) * 0.6;
                x += Math.sin(yaw) * 0.9;
                z += Math.cos(yaw) * 0.9;
                y += (random.nextDouble() - 0.5) * 0.35;
                if (x < 4 || x >= width - 4 || z < 4 || z >= depth - 4 || y < 3) break;
            }
        }
    }

    private void carveSphere(int cx, int cy, int cz, int radius, int[][] surface) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!inBounds(x, y, z) || y <= 1) continue;
                    if ((x-cx)*(x-cx)+(y-cy)*(y-cy)+(z-cz)*(z-cz) <= radius*radius
                            && y < surface[clamp(x,0,width-1)][clamp(z,0,depth-1)] - 4) {
                        set(x, y, z, Block.AIR);
                    }
                }
            }
        }
    }

    private void growNature(Random random, int[][] surface) {
        for (int x = 4; x < width - 4; x++) {
            for (int z = 4; z < depth - 4; z++) {
                int y = surface[x][z];
                if (get(x, y, z) != Block.GRASS) continue;
                double r = random.nextDouble();
                if (r < 0.026 && farFromCenter(x, z, 8)) growTree(x, y + 1, z, random);
                else if (r < 0.065) set(x, y + 1, z, Block.FLOWER);
            }
        }
    }

    private boolean farFromCenter(int x, int z, int radius) {
        int dx = x - width / 2, dz = z - depth / 2;
        return dx * dx + dz * dz > radius * radius;
    }

    private void growTree(int x, int y, int z, Random random) {
        int trunk = 3 + random.nextInt(3);
        for (int i = 0; i < trunk; i++) set(x, y + i, z, Block.WOOD);
        int crownY = y + trunk - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int metric = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy);
                    if (metric <= 3 && get(x + dx, crownY + dy, z + dz) == Block.AIR)
                        set(x + dx, crownY + dy, z + dz, Block.LEAVES);
                }
            }
        }
    }

    private void buildShrine() {
        int baseY = surfaceY(shrineX, shrineZ) + 1;
        for (int x = shrineX - 3; x <= shrineX + 3; x++) {
            for (int z = shrineZ - 3; z <= shrineZ + 3; z++) {
                if (Math.abs(x - shrineX) == 3 && Math.abs(z - shrineZ) == 3) continue;
                set(x, baseY, z, Block.BRICKS);
                for (int y = baseY + 1; y <= baseY + 4; y++) {
                    Block current = get(x, y, z);
                    if (current == Block.WOOD || current == Block.LEAVES) set(x, y, z, Block.AIR);
                }
            }
        }
        int[][] corners = {{-2,-2},{2,-2},{-2,2},{2,2}};
        for (int[] c : corners) {
            for (int y = 1; y <= 3; y++) set(shrineX + c[0], baseY + y, shrineZ + c[1], Block.GOLD_ORE);
            set(shrineX + c[0], baseY + 4, shrineZ + c[1], Block.GLOW);
        }
        set(shrineX, baseY + 1, shrineZ, Block.MYSTERY);
        set(shrineX, baseY + 2, shrineZ, Block.GLOW);
    }

    private void clearSpawn() {
        int sx = width / 2, sz = depth / 2;
        int sy = surfaceY(sx, sz);
        for (int x = sx - 2; x <= sx + 2; x++) {
            for (int z = sz - 2; z <= sz + 2; z++) {
                for (int y = sy + 1; y <= sy + 5; y++) set(x, y, z, Block.AIR);
            }
        }
    }

    public RaycastHit cast(double ox, double oy, double oz, double dx, double dy, double dz,
                           double maxDistance, RaycastHit out) {
        out.clear(maxDistance);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) return out;
        dx /= length; dy /= length; dz /= length;

        int x = fastFloor(ox), y = fastFloor(oy), z = fastFloor(oz);
        int stepX = dx >= 0 ? 1 : -1, stepY = dy >= 0 ? 1 : -1, stepZ = dz >= 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double maxX = dx == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? x + 1 - ox : ox - x) * deltaX);
        double maxY = dy == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? y + 1 - oy : oy - y) * deltaY);
        double maxZ = dz == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? z + 1 - oz : oz - z) * deltaZ);
        int nx = 0, ny = 0, nz = 0;
        double distance = 0.0;

        for (int steps = 0; steps < 256 && distance <= maxDistance; steps++) {
            Block block = get(x, y, z);
            if (block.isRenderable()) {
                out.hit = true;
                out.x = x; out.y = y; out.z = z;
                out.normalX = nx; out.normalY = ny; out.normalZ = nz;
                out.distance = distance;
                out.worldX = ox + dx * distance;
                out.worldY = oy + dy * distance;
                out.worldZ = oz + dz * distance;
                out.block = block;
                return out;
            }
            if (maxX < maxY && maxX < maxZ) {
                x += stepX; distance = maxX; maxX += deltaX; nx = -stepX; ny = 0; nz = 0;
            } else if (maxY < maxZ) {
                y += stepY; distance = maxY; maxY += deltaY; nx = 0; ny = -stepY; nz = 0;
            } else {
                z += stepZ; distance = maxZ; maxZ += deltaZ; nx = 0; ny = 0; nz = -stepZ;
            }
            if (y >= height + 2 || x < -1 || z < -1 || x > width || z > depth) break;
        }
        return out;
    }

    private static int fastFloor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
