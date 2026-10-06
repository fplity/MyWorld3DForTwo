package com.myworld3d;

import java.util.ArrayList;
import java.util.List;

/** A block output or a permanent tool-tier upgrade. */
public final class Recipe {
    public final String name;
    public final int[] costs = new int[Block.values().length];
    public final Block output;
    public final int outputAmount;
    public final int toolTier;

    private Recipe(String name, Block output, int outputAmount, int toolTier, Object... ingredients) {
        this.name = name;
        this.output = output;
        this.outputAmount = outputAmount;
        this.toolTier = toolTier;
        for (int i = 0; i < ingredients.length; i += 2)
            costs[((Block) ingredients[i]).id] = (Integer) ingredients[i + 1];
    }

    public boolean canCraft(Inventory inventory, Player player) {
        if (toolTier > 0 && player.toolTier >= toolTier) return false;
        for (Block block : Block.values()) if (inventory.count(block) < costs[block.id]) return false;
        return true;
    }

    public boolean craft(Inventory inventory, Player player) {
        if (!canCraft(inventory, player)) return false;
        for (Block block : Block.values()) if (costs[block.id] > 0) inventory.remove(block, costs[block.id]);
        if (output != null) inventory.add(output, outputAmount);
        if (toolTier > 0) player.toolTier = toolTier;
        return true;
    }

    public String requirementText() {
        List<String> parts = new ArrayList<>();
        for (Block block : Block.values()) if (costs[block.id] > 0)
            parts.add(block.displayName + "×" + costs[block.id]);
        return String.join(" + ", parts);
    }

    public static List<Recipe> defaults() {
        return List.of(
                new Recipe("木板 ×4", Block.PLANKS, 4, 0, Block.WOOD, 1),
                new Recipe("石砖 ×4", Block.BRICKS, 4, 0, Block.STONE, 2, Block.DIRT, 1),
                new Recipe("玻璃 ×4", Block.GLASS, 4, 0, Block.SAND, 2, Block.COAL_ORE, 1),
                new Recipe("萤石 ×2", Block.GLOW, 2, 0, Block.COAL_ORE, 1, Block.GOLD_ORE, 1),
                new Recipe("铁镐（永久加速）", null, 0, 2, Block.IRON_ORE, 3, Block.WOOD, 2),
                new Recipe("星金镐（极速挖掘）", null, 0, 3, Block.GOLD_ORE, 3, Block.GLOW, 2, Block.WOOD, 2),
                new Recipe("星愿方块", Block.MYSTERY, 1, 0, Block.FLOWER, 4, Block.GLOW, 2)
        );
    }
}
