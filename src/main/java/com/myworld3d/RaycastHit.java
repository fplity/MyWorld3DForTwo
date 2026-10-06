package com.myworld3d;

/** Mutable to avoid allocating one object for every rendered pixel. */
public final class RaycastHit {
    public boolean hit;
    public int x, y, z;
    public int normalX, normalY, normalZ;
    public double distance;
    public double worldX, worldY, worldZ;
    public Block block = Block.AIR;

    public void clear(double maxDistance) {
        hit = false;
        block = Block.AIR;
        distance = maxDistance;
        normalX = normalY = normalZ = 0;
    }
}
