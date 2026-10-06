package com.myworld3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Lightweight voxel-world creatures: hostile slimes, fireflies, and the hidden companion. */
public final class EntitySystem {
    private final List<Creature> creatures = new ArrayList<>();
    private final Random random;
    private double slimeSpawnTimer = 8;
    private double companionHealTimer;
    private boolean companionUnlocked;

    public EntitySystem(World world, boolean companionUnlocked) {
        random = new Random(world.seed ^ 0x51A7E55L);
        for (int i = 0; i < 10; i++) spawnSlime(world);
        for (int i = 0; i < 26; i++) spawnFirefly(world);
        if (companionUnlocked) unlockCompanion(world.width / 2.0, world.surfaceY(world.width/2, world.depth/2) + 3, world.depth / 2.0);
    }

    public List<Creature> creatures() { return creatures; }
    public boolean isCompanionUnlocked() { return companionUnlocked; }

    public void unlockCompanion(double x, double y, double z) {
        if (companionUnlocked) return;
        companionUnlocked = true;
        creatures.add(new Creature(Creature.Kind.COMPANION, x, y, z, random.nextDouble() * 10));
    }

    public void update(World world, Player player, double time, boolean rainy, double dt) {
        boolean night = daylight(time) < 0.35;
        slimeSpawnTimer -= dt;
        if (slimeSpawnTimer <= 0 && night && count(Creature.Kind.SLIME) < 14) {
            spawnSlimeNear(world, player); slimeSpawnTimer = 8 + random.nextDouble() * 8;
        }
        for (Creature c : creatures) {
            c.phase += dt;
            switch (c.kind) {
                case SLIME -> updateSlime(world, player, c, night, dt);
                case FIREFLY -> updateFirefly(world, c, night, rainy, dt);
                case COMPANION -> updateCompanion(world, player, c, dt);
            }
        }
        creatures.removeIf(c -> c.health <= 0);
        if (companionUnlocked) {
            companionHealTimer -= dt;
            if (companionHealTimer <= 0 && player.health < 20) {
                Creature companion = creatures.stream().filter(c -> c.kind == Creature.Kind.COMPANION).findFirst().orElse(null);
                if (companion != null && distance(companion.x, companion.y, companion.z, player.x, player.y, player.z) < 4) {
                    player.heal(1); companionHealTimer = 9;
                }
            }
        }
    }

    private void updateSlime(World world, Player player, Creature c, boolean night, double dt) {
        double dx = player.x - c.x, dz = player.z - c.z;
        double distance = Math.hypot(dx, dz);
        double speed = night && distance < 15 ? 1.65 : 0.55;
        if (distance > 0.01) { c.vx = dx / distance * speed; c.vz = dz / distance * speed; }
        if (c.y <= world.surfaceY(floor(c.x), floor(c.z)) + 1.02) {
            c.y = world.surfaceY(floor(c.x), floor(c.z)) + 1.02;
            c.vy = 3.1 + random.nextDouble();
        }
        c.vy -= 9.5 * dt;
        double nx = c.x + c.vx * dt, nz = c.z + c.vz * dt;
        int ground = world.surfaceY(floor(nx), floor(nz));
        if (ground < c.y + 1.2 && !world.get(floor(nx), floor(c.y + 0.2), floor(nz)).solid) { c.x = nx; c.z = nz; }
        c.y = Math.max(ground + 1.0, c.y + c.vy * dt);
        if (distance < 1.15 && Math.abs(c.y - player.y) < 1.8) player.hurt(2);
    }

    private void updateFirefly(World world, Creature c, boolean night, boolean rainy, double dt) {
        double speed = rainy ? 0.08 : 0.23;
        c.x += Math.sin(c.phase * 1.7) * dt * speed;
        c.z += Math.cos(c.phase * 1.3) * dt * speed;
        int ground = world.surfaceY(floor(c.x), floor(c.z));
        c.y = ground + 2.0 + Math.sin(c.phase * 2.2) * 0.75;
        if (!night) c.y = ground + 0.25;
    }

    private void updateCompanion(World world, Player player, Creature c, double dt) {
        double desiredX = player.x - Math.cos(player.yaw) * 1.4;
        double desiredZ = player.z + Math.sin(player.yaw) * 1.4;
        c.x += (desiredX - c.x) * Math.min(1, dt * 2.8);
        c.z += (desiredZ - c.z) * Math.min(1, dt * 2.8);
        c.y += (player.y + 1.35 + Math.sin(c.phase * 3) * 0.22 - c.y) * Math.min(1, dt * 3.5);
    }

    public Creature aimedCreature(World world, Player player, double maxDistance) {
        RaycastHit blockHit = world.castIgnoringLiquids(player.x, player.cameraY(), player.z,
                player.lookX(), player.lookY(), player.lookZ(), maxDistance, new RaycastHit());
        double blockDistance = blockHit.hit ? blockHit.distance : maxDistance;
        return creatures.stream().filter(c -> c.kind == Creature.Kind.SLIME).filter(c -> {
            double rx = c.x - player.x, ry = c.y + c.height() * .5 - player.cameraY(), rz = c.z - player.z;
            double distance = Math.sqrt(rx*rx + ry*ry + rz*rz);
            if (distance > maxDistance || distance > blockDistance + 0.3) return false;
            double dot = (rx*player.lookX()+ry*player.lookY()+rz*player.lookZ()) / Math.max(.001, distance);
            return dot > 1.0 - 0.18 / Math.max(1.0, distance);
        }).min(Comparator.comparingDouble(c -> distance(c.x,c.y,c.z,player.x,player.y,player.z))).orElse(null);
    }

    public boolean attack(Creature creature, Inventory inventory) {
        if (creature == null || creature.kind != Creature.Kind.SLIME || creature.health <= 0) return false;
        creature.health--;
        if (creature.health <= 0) inventory.add(random.nextBoolean() ? Block.COAL_ORE : Block.GLOW, 1);
        return true;
    }

    private void spawnSlime(World world) {
        int x = 3 + random.nextInt(Math.max(1, world.width - 6)), z = 3 + random.nextInt(Math.max(1, world.depth - 6));
        creatures.add(new Creature(Creature.Kind.SLIME, x + .5, world.surfaceY(x,z)+1.02, z+.5, random.nextDouble()*8));
    }

    private void spawnSlimeNear(World world, Player player) {
        double angle = random.nextDouble() * Math.PI * 2;
        int x = clamp((int)(player.x + Math.sin(angle) * 12), 3, world.width-4);
        int z = clamp((int)(player.z + Math.cos(angle) * 12), 3, world.depth-4);
        creatures.add(new Creature(Creature.Kind.SLIME, x+.5, world.surfaceY(x,z)+1.02, z+.5, random.nextDouble()*8));
    }

    private void spawnFirefly(World world) {
        int x = 3 + random.nextInt(world.width - 6), z = 3 + random.nextInt(world.depth - 6);
        creatures.add(new Creature(Creature.Kind.FIREFLY, x+.5, world.surfaceY(x,z)+2, z+.5, random.nextDouble()*20));
    }

    private int count(Creature.Kind kind) { return (int) creatures.stream().filter(c -> c.kind == kind).count(); }
    private static int floor(double v) { int i=(int)v; return v<i?i-1:i; }
    private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static double distance(double ax,double ay,double az,double bx,double by,double bz){
        double x=ax-bx,y=ay-by,z=az-bz; return Math.sqrt(x*x+y*y+z*z);
    }
    public static double daylight(double time) { return Math.max(0.1, Math.min(1.0, 0.18 + Math.sin(time*Math.PI*2)*0.82)); }
}
