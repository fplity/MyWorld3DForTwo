package com.myworld3d;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;

/**
 * Pure-Java voxel ray caster. It intentionally renders at a small internal resolution,
 * producing a crisp pixel-art look when the game canvas scales it up.
 */
public final class Renderer {
    public static final int INTERNAL_WIDTH = 480;
    public static final int INTERNAL_HEIGHT = 270;
    private static final double FOV = Math.toRadians(77);
    private static final double MAX_DISTANCE = 58.0;

    private final World world;
    private final BufferedImage image = new BufferedImage(INTERNAL_WIDTH, INTERNAL_HEIGHT, BufferedImage.TYPE_INT_RGB);
    private final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private final float[] depth = new float[INTERNAL_WIDTH * INTERNAL_HEIGHT];
    private final RaycastHit scratch = new RaycastHit();
    private final double[] screenX = new double[INTERNAL_WIDTH];
    private final double[] screenY = new double[INTERNAL_HEIGHT];
    private final double[] rayScale = new double[INTERNAL_WIDTH * INTERNAL_HEIGHT];

    public Renderer(World world) {
        this.world = world;
        double tanHalf=Math.tan(FOV/2), aspect=INTERNAL_WIDTH/(double)INTERNAL_HEIGHT;
        for(int x=0;x<INTERNAL_WIDTH;x++) screenX[x]=((x+.5)*2/INTERNAL_WIDTH-1)*tanHalf*aspect;
        for(int y=0;y<INTERNAL_HEIGHT;y++) screenY[y]=(1-(y+.5)*2/INTERNAL_HEIGHT)*tanHalf;
        for(int y=0;y<INTERNAL_HEIGHT;y++)for(int x=0;x<INTERNAL_WIDTH;x++)
            rayScale[y*INTERNAL_WIDTH+x]=Math.sqrt(1+screenX[x]*screenX[x]+screenY[y]*screenY[y]);
    }
    public BufferedImage image() { return image; }

    public void render(Player player, double time, boolean rainy, RaycastHit target,
                       List<Creature> creatures, double secretGlow) {
        final int w = INTERNAL_WIDTH, h = INTERNAL_HEIGHT;
        final double tanHalf = Math.tan(FOV / 2.0);
        final double sinYaw = Math.sin(player.yaw), cosYaw = Math.cos(player.yaw);
        final double sinPitch = Math.sin(player.pitch), cosPitch = Math.cos(player.pitch);
        final double fx = sinYaw * cosPitch, fy = sinPitch, fz = cosYaw * cosPitch;
        final double rx = cosYaw, ry = 0, rz = -sinYaw;
        final double ux = -sinYaw * sinPitch, uy = cosPitch, uz = -cosYaw * sinPitch;
        final double daylight = EntitySystem.daylight(time);
        final double sunAngle = time * Math.PI * 2.0;
        final int fogColor = skyColor(0.02, fx, fy, fz, daylight, sunAngle, rainy, secretGlow);

        for (int py = 0; py < h; py++) {
            double sy = screenY[py];
            for (int px = 0; px < w; px++) {
                int index = py * w + px;
                double sx=screenX[px];
                double dx = fx + rx * sx + ux * sy;
                double dy = fy + ry * sx + uy * sy;
                double dz = fz + rz * sx + uz * sy;
                double inv = 1.0 / rayScale[index];
                dx *= inv; dy *= inv; dz *= inv;
                world.castNormalized(player.x, player.cameraY(), player.z, dx, dy, dz, MAX_DISTANCE, scratch, false);
                if (!scratch.hit) {
                    pixels[index] = skyColor(dy, dx, dy, dz, daylight, sunAngle, rainy, secretGlow);
                    depth[index] = (float) MAX_DISTANCE;
                    continue;
                }
                depth[index] = (float) scratch.distance;
                int color = blockColor(scratch, daylight, time, target);
                double fogStart = rainy ? 14.0 : 29.0;
                double fogRange = rainy ? 24.0 : 29.0;
                double fog = clamp((scratch.distance - fogStart) / fogRange, 0, 0.94);
                pixels[index] = mix(color, fogColor, fog);
            }
        }
        drawCreatures(player, creatures, daylight, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf);
        if (world.get(floor(player.x), floor(player.cameraY()), floor(player.z)) == Block.WATER)
            overlayUnderwater();
    }

    private int blockColor(RaycastHit hit, double daylight, double time, RaycastHit target) {
        Block block = hit.block;
        int color = block.color;
        double normalLight = hit.normalY > 0 ? 1.08 : hit.normalY < 0 ? 0.53
                : hit.normalX != 0 ? 0.82 : 0.70;
        double light = (0.28 + daylight * 0.72) * normalLight;
        if (block == Block.GLOW) light = Math.max(light, 1.12);
        if (block == Block.MYSTERY) {
            double pulse = 0.5 + 0.5 * Math.sin(time * Math.PI * 12 + hit.x + hit.z);
            color = mix(0x6E5BE7, 0xF17BD2, pulse);
            light = Math.max(light, 1.05);
        }
        if (block == Block.WATER) {
            color = mix(color, 0x73C8E9, 0.15 + 0.1 * Math.sin(hit.worldX * 3 + hit.worldZ * 2 + time * 20));
            light *= 0.92;
        }
        if (block == Block.GLASS) light *= 1.1;

        int checker = floor(hit.worldX * 5) * 31 + floor(hit.worldY * 5) * 17 + floor(hit.worldZ * 5) * 13;
        double variation = ((checker ^ (checker >>> 3)) & 3) * 0.025 - 0.035;
        if (block == Block.GRASS && hit.normalY <= 0) color = mix(color, Block.DIRT.color, 0.32);
        if (block == Block.WOOD && hit.normalY == 0) {
            int rings = floor((hit.worldY - Math.floor(hit.worldY)) * 7);
            variation += (rings & 1) == 0 ? -0.07 : 0.04;
        }
        color = multiply(color, light + variation);

        if (target != null && target.hit && target.x == hit.x && target.y == hit.y && target.z == hit.z) {
            double u = fractional(hit.normalX!=0 ? hit.worldZ : hit.worldX);
            double v = fractional(hit.normalY!=0 ? hit.worldZ : hit.worldY);
            double edge = Math.min(Math.min(u,1-u), Math.min(v,1-v));
            if (edge < 0.035) color = mix(color, 0xFFFFFF, 0.80);
            else color = multiply(color, 1.08);
        }
        return color;
    }

    private int skyColor(double vertical, double dx, double dy, double dz, double daylight,
                         double sunAngle, boolean rainy, double secretGlow) {
        double horizon = clamp(vertical * 0.7 + 0.52, 0, 1);
        int dayBottom = rainy ? 0x8396A5 : 0xA5D7EE;
        int dayTop = rainy ? 0x536371 : 0x4B91E5;
        int nightBottom = 0x121A35, nightTop = 0x030611;
        int day = mix(dayBottom, dayTop, horizon);
        int night = mix(nightBottom, nightTop, horizon);
        int color = mix(night, day, daylight);

        double sunX = Math.cos(sunAngle), sunY = Math.sin(sunAngle), sunZ = 0.18;
        double sunInv = 1.0 / Math.sqrt(sunX*sunX + sunY*sunY + sunZ*sunZ);
        double sunDot = dx*sunX*sunInv + dy*sunY*sunInv + dz*sunZ*sunInv;
        if (-sunDot > 0.997 && daylight < .38) color = mix(color, 0xE5E9FF, .95);
        else if (sunDot > 0.9965 && sunY > 0) color = mix(color, 0xFFF3A3, 0.95);
        else if (sunDot > 0.988) color = mix(color, 0xFFD991, (sunDot - .988) * 45 * daylight);

        if (daylight < 0.38 && dy > -0.05) {
            int ax = floor((Math.atan2(dx, dz) + Math.PI) * 170.0);
            int ay = floor((Math.asin(clamp(dy,-1,1)) + Math.PI/2) * 130.0);
            long hash = (ax * 73856093L) ^ (ay * 19349663L) ^ world.seed;
            hash ^= hash >>> 17;
            if ((hash & 0x1FFF) < 5) color = mix(color, 0xE7F4FF, 0.75 * (0.38-daylight)/0.28);
        }
        if (secretGlow > 0) {
            double rainbow = 0.5 + 0.5 * Math.sin(Math.atan2(dx,dz)*3 + vertical*5 + secretGlow*8);
            int magic = mix(0x8BE9E0, 0xE686D7, rainbow);
            color = mix(color, magic, Math.min(.28, secretGlow * .045));
        }
        return color;
    }

    private void drawCreatures(Player player, List<Creature> creatures, double daylight,
                               double fx, double fy, double fz, double rx, double rz,
                               double ux, double uy, double uz, double tanHalf) {
        int w = INTERNAL_WIDTH, h = INTERNAL_HEIGHT;
        double focal = h / (2.0 * tanHalf);
        for (Creature c : creatures) {
            if(c.health<=0 || (c.kind==Creature.Kind.FIREFLY && daylight>.38)) continue;
            double relX = c.x - player.x;
            double relY = c.y + c.height() * .5 - player.cameraY();
            double relZ = c.z - player.z;
            double cameraZ = relX*fx + relY*fy + relZ*fz;
            if (cameraZ < 0.15 || cameraZ > MAX_DISTANCE) continue;
            double cameraX = relX*rx + relZ*rz;
            double cameraY = relX*ux + relY*uy + relZ*uz;
            int centerX = (int)(w/2.0 + cameraX/cameraZ*focal);
            int centerY = (int)(h/2.0 - cameraY/cameraZ*focal);
            int size = Math.max(c.kind == Creature.Kind.FIREFLY ? 2 : 3,
                    (int)(c.height()/cameraZ*focal));
            if (centerX + size < 0 || centerX - size >= w || centerY + size < 0 || centerY - size >= h) continue;
            drawCreature(c, centerX, centerY, size, (float)cameraZ, daylight);
        }
    }

    private void drawCreature(Creature c, int cx, int cy, int size, float z, double daylight) {
        int half = Math.max(1, size / 2);
        int left = Math.max(0, cx-half), right = Math.min(INTERNAL_WIDTH-1, cx+half);
        int top = Math.max(0, cy-half), bottom = Math.min(INTERNAL_HEIGHT-1, cy+half);
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int localX = x - cx, localY = y - cy;
                boolean inside;
                int color;
                if (c.kind == Creature.Kind.FIREFLY) {
                    inside = localX*localX + localY*localY <= half*half;
                    color = ((x+y+(int)(c.phase*8))&1)==0 ? 0xFFF79A : 0xFFD83D;
                } else if (c.kind == Creature.Kind.COMPANION) {
                    inside = Math.abs(localX) + Math.abs(localY) <= half + Math.max(1, half/3);
                    color = localY > 0 && Math.abs(localX) < Math.max(1, half/3) ? 0xF06FB2 : 0xFFE05D;
                } else {
                    int rounded = Math.max(1, half/4);
                    inside = Math.abs(localX) <= half && Math.abs(localY) <= half &&
                            !(Math.abs(localX)>half-rounded && Math.abs(localY)>half-rounded);
                    boolean eye = localY < 0 && localY > -half/2-2 &&
                            (Math.abs(localX-half/3) <= Math.max(1,size/12) || Math.abs(localX+half/3) <= Math.max(1,size/12));
                    boolean mouth = localY > 0 && localY < half/2 && Math.abs(localX) < half/3;
                    color = eye ? 0x13221A : mouth ? 0x26532D :
                            multiply(((x+y)&1)==0 ? 0x6CD85D : 0x53BA4B, .35 + daylight*.65);
                }
                int index = y*INTERNAL_WIDTH+x;
                float distance=(float)(z*rayScale[index]);
                if (inside && distance < depth[index]) {
                    pixels[index] = color;
                    depth[index] = distance;
                }
            }
        }
    }

    private void overlayUnderwater() {
        for (int i = 0; i < pixels.length; i++) pixels[i] = mix(pixels[i], 0x185E9A, 0.42);
    }

    private static int multiply(int color, double factor) {
        int r = clamp255((int)(((color>>16)&255)*factor));
        int g = clamp255((int)(((color>>8)&255)*factor));
        int b = clamp255((int)((color&255)*factor));
        return (r<<16)|(g<<8)|b;
    }
    public static int mix(int a, int b, double t) {
        t = clamp(t,0,1);
        int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int bl=(int)((a&255)*(1-t)+(b&255)*t);
        return (r<<16)|(g<<8)|bl;
    }
    private static int clamp255(int v){return Math.max(0,Math.min(255,v));}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double fractional(double v){return v-Math.floor(v);}
    private static int floor(double v){int i=(int)v;return v<i?i-1:i;}
}
