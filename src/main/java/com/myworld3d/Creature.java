package com.myworld3d;

public final class Creature {
    public enum Kind { SLIME, FIREFLY, COMPANION }
    public final Kind kind;
    public double x, y, z;
    public double vx, vy, vz;
    public double phase;
    public int health;

    public Creature(Kind kind, double x, double y, double z, double phase) {
        this.kind = kind; this.x = x; this.y = y; this.z = z; this.phase = phase;
        this.health = kind == Kind.SLIME ? 3 : 1;
    }

    public double height() { return kind == Kind.SLIME ? 0.9 : kind == Kind.COMPANION ? 0.65 : 0.14; }
}
