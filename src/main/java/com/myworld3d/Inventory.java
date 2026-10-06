package com.myworld3d;

import java.util.List;

public final class Inventory {
    private final int[] counts = new int[Block.values().length];
    private final List<Recipe> recipes = Recipe.defaults();
    public int selected;
    public int selectedRecipe;

    public Inventory(boolean starterKit) {
        if (starterKit) {
            add(Block.DIRT, 28); add(Block.STONE, 18); add(Block.WOOD, 8);
            add(Block.PLANKS, 16); add(Block.BRICKS, 12); add(Block.GLASS, 8);
            add(Block.GLOW, 4); add(Block.SAND, 12); add(Block.GRASS, 8); add(Block.FLOWER, 2);
        }
    }

    public int count(Block block) { return counts[block.id]; }
    public void add(Block block, int amount) {
        if (block != Block.AIR && block != Block.WATER) counts[block.id] = Math.min(999, counts[block.id] + amount);
    }
    public boolean remove(Block block, int amount) {
        if (counts[block.id] < amount) return false;
        counts[block.id] -= amount;
        return true;
    }
    public Block selectedBlock() { return Block.BUILD_PALETTE[Math.floorMod(selected, Block.BUILD_PALETTE.length)]; }
    public void scroll(int delta) { selected = Math.floorMod(selected + delta, Block.BUILD_PALETTE.length); }
    public List<Recipe> recipes() { return recipes; }
    public Recipe selectedRecipe() { return recipes.get(Math.floorMod(selectedRecipe, recipes.size())); }
    public int[] copyCounts() { return counts.clone(); }
    public void replaceCounts(int[] data) {
        for (int i = 0; i < counts.length; i++) counts[i] = i < data.length ? Math.max(0, data[i]) : 0;
    }
}
