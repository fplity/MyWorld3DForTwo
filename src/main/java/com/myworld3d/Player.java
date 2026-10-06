package com.myworld3d;

import java.awt.event.KeyEvent;

/** First-person controller using an AABB body and axis-separated voxel collisions. */
public final class Player {
    public static final double WIDTH = 0.58;
    public static final double HEIGHT = 1.78;
    public static final double EYE_HEIGHT = 1.57;

    public double x, y, z;
    public double yaw, pitch;
    public double velocityX, velocityY, velocityZ;
    public boolean grounded;
    public boolean flying;
    public int health = 20;
    public int toolTier = 1;
    private double hurtCooldown;

    public Player(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public double cameraY() { return y + EYE_HEIGHT; }
    public double lookX() { return Math.sin(yaw) * Math.cos(pitch); }
    public double lookY() { return Math.sin(pitch); }
    public double lookZ() { return Math.cos(yaw) * Math.cos(pitch); }

    public void update(World world, Input input, double dt) {
        hurtCooldown = Math.max(0.0, hurtCooldown - dt);
        double sensitivity = 0.00235;
        yaw += input.consumeMouseDX() * sensitivity;
        pitch -= input.consumeMouseDY() * sensitivity;
        pitch = Math.max(-1.48, Math.min(1.48, pitch));

        double forward = (input.isDown(KeyEvent.VK_W) ? 1 : 0) - (input.isDown(KeyEvent.VK_S) ? 1 : 0);
        double strafe = (input.isDown(KeyEvent.VK_D) ? 1 : 0) - (input.isDown(KeyEvent.VK_A) ? 1 : 0);
        double magnitude = Math.hypot(forward, strafe);
        if (magnitude > 1.0) { forward /= magnitude; strafe /= magnitude; }
        boolean sprint = input.isDown(KeyEvent.VK_SHIFT);
        double speed = flying ? 7.8 : (sprint ? 6.4 : 4.25);
        boolean inWater = world.get(floor(x), floor(y + 0.7), floor(z)).liquid;
        if (inWater && !flying) speed *= 0.62;

        double desiredX = (Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        double desiredZ = (Math.cos(yaw) * forward - Math.sin(yaw) * strafe) * speed;
        double acceleration = flying ? 12.0 : (grounded ? 18.0 : 5.0);
        velocityX = approach(velocityX, desiredX, acceleration * dt);
        velocityZ = approach(velocityZ, desiredZ, acceleration * dt);

        if (flying) {
            velocityY = ((input.isDown(KeyEvent.VK_SPACE) ? 1 : 0) -
                    (input.isDown(KeyEvent.VK_SHIFT) ? 1 : 0)) * speed;
        } else {
            if (input.consumePressed(KeyEvent.VK_SPACE) && (grounded || inWater))
                velocityY = inWater ? 5.0 : 7.35;
            velocityY -= (inWater ? 5.5 : 20.5) * dt;
            velocityY = Math.max(velocityY, -24.0);
        }

        moveAxis(world, velocityX * dt, 0, 0);
        grounded = false;
        moveAxis(world, 0, velocityY * dt, 0);
        moveAxis(world, 0, 0, velocityZ * dt);
        x = Math.max(1.3, Math.min(world.width - 1.3, x));
        z = Math.max(1.3, Math.min(world.depth - 1.3, z));
    }

    private void moveAxis(World world, double dx, double dy, double dz) {
        double amount = dx != 0 ? dx : (dy != 0 ? dy : dz);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(amount) / 0.18));
        dx /= steps; dy /= steps; dz /= steps;
        for (int i = 0; i < steps; i++) {
            double nx = x + dx, ny = y + dy, nz = z + dz;
            if (!collides(world, nx, ny, nz)) {
                x = nx; y = ny; z = nz;
            } else {
                if (dy < 0) grounded = true;
                if (dx != 0) velocityX = 0;
                if (dy != 0) velocityY = 0;
                if (dz != 0) velocityZ = 0;
                break;
            }
        }
    }

    public boolean collides(World world, double px, double py, double pz) {
        double half = WIDTH / 2.0;
        int minX = floor(px - half), maxX = floor(px + half);
        int minY = floor(py + 0.02), maxY = floor(py + HEIGHT - 0.02);
        int minZ = floor(pz - half), maxZ = floor(pz + half);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++)
                    if (world.get(bx, by, bz).solid) return true;
        return false;
    }

    public boolean intersectsBlock(int bx, int by, int bz) {
        double half = WIDTH / 2.0;
        return x + half > bx && x - half < bx + 1 && y + HEIGHT > by && y < by + 1
                && z + half > bz && z - half < bz + 1;
    }

    public void hurt(int amount) {
        if (hurtCooldown > 0 || flying) return;
        health = Math.max(0, health - amount);
        hurtCooldown = 0.85;
    }

    public void heal(int amount) { health = Math.min(20, health + amount); }

    public void respawn(World world) {
        x = world.width / 2.0 + 0.5;
        z = world.depth / 2.0 + 0.5;
        y = world.surfaceY(floor(x), floor(z)) + 1.02;
        velocityX = velocityY = velocityZ = 0;
        health = 20;
    }

    public double miningSpeed() { return toolTier == 3 ? 3.3 : toolTier == 2 ? 2.1 : 1.0; }
    private static double approach(double value, double target, double amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }
    private static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
}
