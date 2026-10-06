package com.myworld3d;

import java.util.HashMap;
import java.util.Map;

/** The complete block palette. IDs are stable because they are written to save files. */
public enum Block {
    AIR(0, "空气", 0x000000, false, false, 0.0, false),
    GRASS(1, "草方块", 0x62A84A, true, false, 0.45, true),
    DIRT(2, "泥土", 0x805536, true, false, 0.40, true),
    STONE(3, "石头", 0x85898C, true, false, 1.15, true),
    SAND(4, "沙子", 0xD8C47A, true, false, 0.35, true),
    WATER(5, "水", 0x397FCC, false, true, -1.0, false),
    WOOD(6, "原木", 0x80502C, true, false, 0.70, true),
    LEAVES(7, "树叶", 0x3D873E, true, false, 0.25, true),
    PLANKS(8, "木板", 0xB8834C, true, false, 0.55, true),
    BRICKS(9, "石砖", 0x777C80, true, false, 1.00, true),
    GLASS(10, "玻璃", 0xA7DDE2, true, false, 0.20, true),
    COAL_ORE(11, "煤矿石", 0x4C4E50, true, false, 1.35, true),
    IRON_ORE(12, "铁矿石", 0xB58C73, true, false, 1.65, true),
    GOLD_ORE(13, "金矿石", 0xE3B638, true, false, 2.10, true),
    GLOW(14, "萤石", 0xFFE46A, true, false, 0.55, true),
    FLOWER(15, "星花", 0xE76FAE, false, false, 0.10, true),
    MYSTERY(16, "星愿方块", 0x8A66E8, true, false, 3.0, true);

    public static final Block[] BUILD_PALETTE = {
            DIRT, STONE, WOOD, PLANKS, BRICKS, GLASS, GLOW, SAND, GRASS,
            LEAVES, COAL_ORE, IRON_ORE, GOLD_ORE, FLOWER, MYSTERY
    };

    private static final Map<Integer, Block> BY_ID = new HashMap<>();
    static { for (Block block : values()) BY_ID.put(block.id, block); }

    public final int id;
    public final String displayName;
    public final int color;
    public final boolean solid;
    public final boolean liquid;
    public final double hardness;
    public final boolean placeable;

    Block(int id, String displayName, int color, boolean solid, boolean liquid,
          double hardness, boolean placeable) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.solid = solid;
        this.liquid = liquid;
        this.hardness = hardness;
        this.placeable = placeable;
    }

    public static Block fromId(int id) { return BY_ID.getOrDefault(id, AIR); }
    public boolean isRenderable() { return this != AIR; }
}
