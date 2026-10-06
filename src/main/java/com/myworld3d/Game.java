package com.myworld3d;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Main loop, gameplay rules, menus, HUD, and integration of all game systems. */
public final class Game extends Canvas implements Runnable {
    private enum Screen { MENU, PLAYING, INVENTORY, PAUSED }
    private static final int VIEW_ASPECT_W = 16, VIEW_ASPECT_H = 9;
    private static final Path SAVE_FILE = Path.of("saves", "world.mw3d");
    private static final Font TITLE_FONT = new Font("Microsoft YaHei", Font.BOLD, 54);
    private static final Font LARGE_FONT = new Font("Microsoft YaHei", Font.BOLD, 25);
    private static final Font UI_FONT = new Font("Microsoft YaHei", Font.PLAIN, 16);
    private static final Font SMALL_FONT = new Font("Microsoft YaHei", Font.PLAIN, 13);

    private final Input input;
    private final Random events;
    private World world;
    private Player player;
    private Inventory inventory;
    private Renderer renderer;
    private EntitySystem entities;
    private final RaycastHit target = new RaycastHit();
    private volatile boolean running;
    private Thread loopThread;
    private Screen screen = Screen.MENU;
    private boolean loadedSave;
    private boolean rainy;
    private boolean secretFound;
    private boolean shrineHintShown;
    private double timeOfDay = 0.23;
    private double miningProgress;
    private int miningX = Integer.MIN_VALUE, miningY, miningZ;
    private double attackCooldown;
    private double autosaveTimer = 30;
    private double weatherTimer = 28;
    private double secretGlow;
    private String toast = "";
    private double toastTimer;
    private int fps;

    public Game() {
        setPreferredSize(new Dimension(1280, 720));
        setIgnoreRepaint(true);
        loadOrCreateWorld();
        input = new Input(this);
        events = new Random(world.seed ^ 0xBEEFBEEFL);
    }

    private void loadOrCreateWorld() {
        if (Files.isRegularFile(SAVE_FILE)) {
            try {
                SaveSystem.Snapshot saved = SaveSystem.load(SAVE_FILE);
                world = saved.world;
                player = new Player(saved.x, saved.y, saved.z);
                player.yaw = saved.yaw; player.pitch = saved.pitch; player.health = saved.health;
                player.toolTier = saved.toolTier; player.flying = saved.flying;
                inventory = new Inventory(false); inventory.replaceCounts(saved.counts); inventory.selected = saved.selected;
                timeOfDay = saved.timeOfDay; rainy = saved.rainy; secretFound = saved.secretFound;
                loadedSave = true;
            } catch (Exception e) {
                Path broken = SAVE_FILE.resolveSibling("world-broken-" + System.currentTimeMillis() + ".mw3d");
                try { Files.move(SAVE_FILE, broken); } catch (IOException ignored) {}
                createWorld();
                toast = "旧存档无法读取，已备份并创建新世界"; toastTimer = 8;
            }
        } else createWorld();
        renderer = new Renderer(world);
        entities = new EntitySystem(world, secretFound);
    }

    private void createWorld() {
        long seed = System.currentTimeMillis() ^ System.nanoTime();
        world = new World(seed);
        int sx = world.width / 2, sz = world.depth / 2;
        player = new Player(sx + .5, world.surfaceY(sx, sz) + 1.02, sz + .5);
        player.yaw = 0.72;
        inventory = new Inventory(true);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        loopThread = new Thread(this, "myworld3d-game-loop");
        loopThread.start();
    }

    public void shutdown() {
        running = false;
        if (loopThread != null && Thread.currentThread() != loopThread) {
            try { loopThread.join(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        saveQuietly(false);
    }

    @Override public void run() {
        requestFocusInWindow();
        long previous = System.nanoTime(), fpsClock = previous;
        int frames = 0;
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - previous) / 1_000_000_000.0);
            previous = now;
            update(dt);
            renderFrame();
            frames++;
            if (now - fpsClock >= 1_000_000_000L) { fps = frames; frames = 0; fpsClock = now; }
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    private void update(double dt) {
        toastTimer = Math.max(0, toastTimer - dt);
        secretGlow = Math.max(0, secretGlow - dt);
        attackCooldown = Math.max(0, attackCooldown - dt);

        if (screen == Screen.MENU) {
            if (input.consumePressed(KeyEvent.VK_ENTER)) enterGame();
            if (input.consumePressed(KeyEvent.VK_N)) startFreshWorld();
            return;
        }
        if (input.consumePressed(KeyEvent.VK_ESCAPE)) {
            if (screen == Screen.PLAYING) { screen = Screen.PAUSED; input.setCaptured(false); }
            else { screen = Screen.PLAYING; input.setCaptured(true); }
            return;
        }
        if (screen == Screen.PAUSED) {
            if (input.consumePressed(KeyEvent.VK_ENTER)) enterGame();
            return;
        }
        if (screen == Screen.INVENTORY) {
            updateInventory();
            return;
        }

        if (input.consumePressed(KeyEvent.VK_E)) {
            screen = Screen.INVENTORY; input.setCaptured(false); miningProgress = 0; return;
        }
        if (input.consumePressed(KeyEvent.VK_F)) {
            player.flying = !player.flying;
            player.velocityY = 0;
            announce(player.flying ? "飞行模式已开启 — 去看看云层之上吧" : "已返回重力模式");
        }
        if (input.consumePressed(KeyEvent.VK_Y)) {
            rainy = !rainy; weatherTimer = 40; announce(rainy ? "雨云正在聚拢" : "天空放晴了");
        }
        if (input.consumePressed(KeyEvent.VK_F5)) saveQuietly(true);
        for (int i = 0; i < 9; i++) if (input.consumePressed(KeyEvent.VK_1 + i)) inventory.selected = i;
        int wheel = input.consumeWheel(); if (wheel != 0) inventory.scroll(wheel);

        player.update(world, input, dt);
        timeOfDay = (timeOfDay + dt / 245.0) % 1.0;
        weatherTimer -= dt;
        if (weatherTimer <= 0) {
            rainy = events.nextDouble() < 0.28;
            weatherTimer = 35 + events.nextDouble() * 55;
            if (rainy) announce("远处传来了雨声");
        }
        autosaveTimer -= dt;
        if (autosaveTimer <= 0) { saveQuietly(false); autosaveTimer = 30; }

        world.cast(player.x, player.cameraY(), player.z, player.lookX(), player.lookY(), player.lookZ(), 6.0, target);
        updateMiningAndBuilding(dt);
        entities.update(world, player, timeOfDay, rainy, dt);
        checkShrine();
        if (player.health <= 0 || player.y < -4) {
            player.respawn(world);
            announce("你在晨光中重新醒来，背包安然无恙");
        }
    }

    private void updateInventory() {
        if (input.consumePressed(KeyEvent.VK_E)) { screen = Screen.PLAYING; input.setCaptured(true); return; }
        if (input.consumePressed(KeyEvent.VK_UP)) inventory.selectedRecipe--;
        if (input.consumePressed(KeyEvent.VK_DOWN)) inventory.selectedRecipe++;
        inventory.selectedRecipe = Math.floorMod(inventory.selectedRecipe, inventory.recipes().size());
        if (input.consumePressed(KeyEvent.VK_ENTER)) {
            Recipe recipe = inventory.selectedRecipe();
            if (recipe.craft(inventory, player)) announce("合成成功：" + recipe.name);
            else announce("材料不足，或你已经拥有这件工具");
        }
    }

    private void updateMiningAndBuilding(double dt) {
        if (input.consumeMousePressed(1) && attackCooldown <= 0) {
            Creature aimed = entities.aimedCreature(world, player, 5.0);
            if (entities.attack(aimed, inventory)) {
                attackCooldown = .32; miningProgress = 0;
                announce(aimed.health <= 0 ? "史莱姆化成了一小团矿物微光" : "击中了史莱姆");
                return;
            }
        }
        if (input.isMouseDown(1) && target.hit && target.block.hardness > 0 && target.block != Block.WATER) {
            if (miningX != target.x || miningY != target.y || miningZ != target.z) {
                miningX = target.x; miningY = target.y; miningZ = target.z; miningProgress = 0;
            }
            double required = Math.max(.12, target.block.hardness * 1.18 / player.miningSpeed());
            miningProgress += dt / required;
            if (miningProgress >= 1) {
                Block mined = target.block;
                world.set(target.x, target.y, target.z, Block.AIR);
                inventory.add(mined == Block.GRASS ? Block.DIRT : mined, 1);
                miningProgress = 0; miningX = Integer.MIN_VALUE;
                if (mined == Block.MYSTERY) revealSecret(target.x + .5, target.y + 1, target.z + .5);
            }
        } else { miningProgress = 0; miningX = Integer.MIN_VALUE; }

        if (input.consumeMousePressed(3) && target.hit) {
            int bx = target.x + target.normalX, by = target.y + target.normalY, bz = target.z + target.normalZ;
            Block selected = inventory.selectedBlock();
            if (world.inBounds(bx,by,bz) && !player.intersectsBlock(bx,by,bz)
                    && (world.get(bx,by,bz) == Block.AIR || world.get(bx,by,bz) == Block.WATER)
                    && inventory.remove(selected, 1)) {
                world.set(bx,by,bz,selected);
            }
        }
    }

    private void checkShrine() {
        double dx = player.x - world.shrineX, dz = player.z - world.shrineZ;
        if (!shrineHintShown && dx*dx + dz*dz < 75) {
            shrineHintShown = true;
            announce(secretFound ? "星光遗迹仍在回应你的脚步" : "空气中有奇怪的音乐……遗迹中央似乎藏着什么");
        }
    }

    private void revealSecret(double x, double y, double z) {
        if (!secretFound) {
            secretFound = true;
            secretGlow = 12;
            entities.unlockCompanion(x,y,z);
            inventory.add(Block.GLOW, 6);
            player.heal(20);
            announce("✨ 你唤醒了「露米」！它会跟随并治疗你 ✨");
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private void enterGame() { screen = Screen.PLAYING; input.setCaptured(true); }
    private void startFreshWorld() {
        createWorld();
        renderer = new Renderer(world);
        entities = new EntitySystem(world, false);
        timeOfDay = .23; rainy = false; secretFound = false; shrineHintShown = false;
        loadedSave = false;
        announce("新的世界已经生成");
        enterGame();
    }
    private void announce(String text) { toast = text; toastTimer = 4.5; }

    private synchronized void saveQuietly(boolean notify) {
        try {
            SaveSystem.save(SAVE_FILE, world, player, inventory, timeOfDay, rainy, secretFound);
            if (notify) announce("世界已保存");
        } catch (IOException e) {
            announce("保存失败：" + e.getMessage());
        }
    }

    private void renderFrame() {
        if (!isDisplayable()) return;
        BufferStrategy strategy = getBufferStrategy();
        if (strategy == null) { createBufferStrategy(2); return; }
        renderer.render(player, timeOfDay, rainy, target, entities.creatures(), secretGlow);
        do {
            do {
                Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                try { drawGame(g); } finally { g.dispose(); }
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }

    private void drawGame(Graphics2D g) {
        int width = getWidth(), height = getHeight();
        g.setColor(Color.BLACK); g.fillRect(0,0,width,height);
        int viewW = Math.min(width, height * VIEW_ASPECT_W / VIEW_ASPECT_H);
        int viewH = Math.min(height, width * VIEW_ASPECT_H / VIEW_ASPECT_W);
        int vx = (width-viewW)/2, vy=(height-viewH)/2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(renderer.image(), vx,vy,viewW,viewH,null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (rainy) drawRain(g,vx,vy,viewW,viewH);
        switch (screen) {
            case MENU -> drawMenu(g,vx,vy,viewW,viewH);
            case PLAYING -> drawHud(g,vx,vy,viewW,viewH);
            case INVENTORY -> { drawHud(g,vx,vy,viewW,viewH); drawInventory(g,vx,vy,viewW,viewH); }
            case PAUSED -> { drawHud(g,vx,vy,viewW,viewH); drawPause(g,vx,vy,viewW,viewH); }
        }
        if (toastTimer > 0) drawToast(g, vx, vy, viewW);
    }

    private void drawHud(Graphics2D g,int vx,int vy,int w,int h) {
        int cx=vx+w/2, cy=vy+h/2;
        g.setStroke(new BasicStroke(2)); g.setColor(new Color(255,255,255,210));
        g.drawLine(cx-9,cy,cx-3,cy);g.drawLine(cx+3,cy,cx+9,cy);g.drawLine(cx,cy-9,cx,cy-3);g.drawLine(cx,cy+3,cx,cy+9);

        g.setFont(UI_FONT);
        shadowText(g,"生命 "+player.health+"/20",vx+20,vy+30, player.health<7?new Color(255,100,100):Color.WHITE);
        String tool=player.toolTier==3?"星金镐":player.toolTier==2?"铁镐":"木手";
        shadowText(g,String.format("%s  |  X %.1f  Y %.1f  Z %.1f",tool,player.x,player.y,player.z),vx+20,vy+54,Color.WHITE);
        shadowText(g,(rainy?"雨天 · ":"")+timeLabel()+"  "+(player.flying?"飞行":"生存")+"  "+fps+" FPS",vx+20,vy+78,Color.WHITE);

        int slots=9, slot=52, start=cx-slots*slot/2;
        for(int i=0;i<slots;i++){
            int paletteIndex=Math.floorMod(inventory.selected-4+i,Block.BUILD_PALETTE.length);
            Block block=Block.BUILD_PALETTE[paletteIndex]; int x=start+i*slot,y=vy+h-68;
            g.setColor(new Color(15,20,28,i==4?225:175));g.fillRoundRect(x,y,47,47,8,8);
            g.setColor(i==4?new Color(255,229,120):new Color(255,255,255,110));g.setStroke(new BasicStroke(i==4?3:1));g.drawRoundRect(x,y,47,47,8,8);
            g.setColor(new Color(block.color));g.fillRect(x+12,y+9,23,23);g.setColor(new Color(255,255,255,90));g.drawLine(x+12,y+9,x+34,y+9);
            g.setFont(SMALL_FONT);shadowText(g,String.valueOf(inventory.count(block)),x+28,y+43,Color.WHITE);
        }
        Block held=inventory.selectedBlock();
        drawCentered(g,held.displayName+" ×"+inventory.count(held),cx,vy+h-76,UI_FONT,Color.WHITE);
        if(miningProgress>0){
            int bw=180,bx=cx-bw/2,by=cy+34;g.setColor(new Color(0,0,0,160));g.fillRoundRect(bx,by,bw,10,5,5);
            g.setColor(new Color(255,220,92));g.fillRoundRect(bx,by,(int)(bw*Math.min(1,miningProgress)),10,5,5);
        }
    }

    private void drawMenu(Graphics2D g,int vx,int vy,int w,int h){
        g.setColor(new Color(3,8,18,150));g.fillRect(vx,vy,w,h);
        int cx=vx+w/2;
        drawCentered(g,"方 块 奇 境 3D",cx,vy+150,TITLE_FONT,new Color(255,236,155));
        drawCentered(g,"一个纯 Java 的体素世界",cx,vy+188,LARGE_FONT,Color.WHITE);
        g.setColor(new Color(10,18,28,205));g.fillRoundRect(cx-270,vy+235,540,190,24,24);
        drawCentered(g,loadedSave?"按 Enter 继续你的世界":"按 Enter 创造新世界",cx,vy+285,LARGE_FONT,new Color(142,236,194));
        if(loadedSave) drawCentered(g,"按 N 创建全新世界",cx,vy+312,UI_FONT,new Color(255,211,145));
        drawCentered(g,"WASD 移动 · 鼠标观察 · 左键挖掘 · 右键建造",cx,vy+345,UI_FONT,Color.WHITE);
        drawCentered(g,"E 背包合成 · F 飞行 · F5 保存 · Y 天气",cx,vy+375,UI_FONT,Color.WHITE);
        drawCentered(g,"世界里藏着一个会回应星光的秘密",cx,vy+410,UI_FONT,new Color(220,185,255));
        drawCentered(g,"世界种子  "+Long.toUnsignedString(world.seed),cx,vy+h-48,SMALL_FONT,new Color(255,255,255,170));
    }

    private void drawPause(Graphics2D g,int vx,int vy,int w,int h){
        g.setColor(new Color(0,0,0,155));g.fillRect(vx,vy,w,h);int cx=vx+w/2;
        drawCentered(g,"游戏已暂停",cx,vy+h/2-35,TITLE_FONT,Color.WHITE);
        drawCentered(g,"按 Enter 或 Esc 继续 · F5 可在游戏中保存",cx,vy+h/2+25,UI_FONT,new Color(220,230,240));
    }

    private void drawInventory(Graphics2D g,int vx,int vy,int w,int h){
        g.setColor(new Color(0,0,0,175));g.fillRect(vx,vy,w,h);
        int panelW=Math.min(900,w-80),panelH=Math.min(520,h-80),px=vx+(w-panelW)/2,py=vy+(h-panelH)/2;
        g.setColor(new Color(22,29,40,244));g.fillRoundRect(px,py,panelW,panelH,22,22);
        g.setColor(new Color(255,255,255,80));g.drawRoundRect(px,py,panelW,panelH,22,22);
        g.setFont(LARGE_FONT);g.setColor(Color.WHITE);g.drawString("背包",px+30,py+45);g.drawString("工作台",px+panelW/2+20,py+45);
        g.setFont(UI_FONT);
        int columnWidth=(panelW/2-50)/3;
        int shown=0;
        for(Block block:Block.values()){
            if(block==Block.AIR||block==Block.WATER)continue;
            int col=shown%3,row=shown/3,x=px+30+col*columnWidth,y=py+85+row*58;
            g.setColor(new Color(block.color));g.fillRoundRect(x,y-24,28,28,5,5);
            shadowText(g,block.displayName,x+36,y-7,Color.WHITE);shadowText(g,"×"+inventory.count(block),x+36,y+12,new Color(190,205,220));shown++;
        }
        int rx=px+panelW/2+20,ry=py+78;
        for(int i=0;i<inventory.recipes().size();i++){
            Recipe recipe=inventory.recipes().get(i);boolean selected=i==inventory.selectedRecipe;
            if(selected){g.setColor(new Color(77,111,137,190));g.fillRoundRect(rx-8,ry+i*56-23,panelW/2-45,50,10,10);}
            g.setFont(UI_FONT);shadowText(g,(selected?"▶ ":"  ")+recipe.name,rx,ry+i*56,recipe.canCraft(inventory,player)?new Color(151,240,181):new Color(210,210,210));
            g.setFont(SMALL_FONT);shadowText(g,recipe.requirementText(),rx+20,ry+i*56+20,new Color(173,188,203));
        }
        drawCentered(g,"↑/↓ 选择 · Enter 合成 · E 返回游戏",px+panelW/2,py+panelH-23,UI_FONT,new Color(255,230,150));
    }

    private void drawRain(Graphics2D g,int vx,int vy,int w,int h){
        g.setColor(new Color(180,215,240,90));g.setStroke(new BasicStroke(1));long tick=System.nanoTime()/20_000_000L;
        for(int i=0;i<75;i++){int x=vx+Math.floorMod(i*193+(int)tick*5,w);int y=vy+Math.floorMod(i*97+(int)tick*13,h);g.drawLine(x,y,x-5,y+14);}
    }

    private void drawToast(Graphics2D g,int vx,int vy,int w){
        g.setFont(UI_FONT);FontMetrics fm=g.getFontMetrics();int bw=fm.stringWidth(toast)+38,x=vx+(w-bw)/2,y=vy+24;
        g.setColor(new Color(12,18,28,220));g.fillRoundRect(x,y,bw,39,16,16);g.setColor(new Color(255,231,139));g.drawRoundRect(x,y,bw,39,16,16);
        drawCentered(g,toast,vx+w/2,y+26,UI_FONT,Color.WHITE);
    }

    private String timeLabel(){
        if(timeOfDay<.12||timeOfDay>.88)return"黎明";
        if(timeOfDay<.42)return"白昼";
        if(timeOfDay<.56)return"黄昏";
        return"星夜";
    }
    private static void shadowText(Graphics2D g,String text,int x,int y,Color color){g.setColor(new Color(0,0,0,190));g.drawString(text,x+2,y+2);g.setColor(color);g.drawString(text,x,y);}
    private static void drawCentered(Graphics2D g,String text,int x,int y,Font font,Color color){g.setFont(font);FontMetrics fm=g.getFontMetrics();shadowText(g,text,x-fm.stringWidth(text)/2,y,color);}
}
