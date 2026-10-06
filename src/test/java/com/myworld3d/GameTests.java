package com.myworld3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.awt.Canvas;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Dependency-free smoke and regression tests; run with test.ps1. */
public final class GameTests {
    private static int passed;

    public static void main(String[] args) throws Exception {
        testDeterministicWorld();
        testRaycast();
        testShrineExists();
        testRecipes();
        testSaveRoundTrip();
        testRendererSmoke();
        testMineableOres();
        testSpawnAndDimensions();
        testSurfaceCache();
        testUnderwaterRaycast();
        testRaysFromAboveWorld();
        testHotbar();
        testInventoryBoundaries();
        testRecipeCapacity();
        testInputCaptureAndFocus();
        testSwimmingAndCollision();
        testCompanionAndDrops();
        testSaveValidationAndBackups();
        testLegacyAndArchivedSave();
        testGameTransitionsAndWorldBackup();
        testRecoveryFromBackup();
        testSelectedFaceOutline();
        testMiningAndPlacement();
        System.out.println("All regression tests passed: " + passed + " groups (OK)");
    }

    private static void testDeterministicWorld() {
        World a = new World(48,32,48,123456L,true);
        World b = new World(48,32,48,123456L,true);
        check(Arrays.equals(a.copyBlocks(), b.copyBlocks()), "相同种子应生成相同世界");
        int centerY=a.surfaceY(24,24);
        check(a.get(24,centerY,24).solid,"出生点脚下必须是实体地面");
        pass("Deterministic terrain");
    }

    private static void testRaycast() {
        World world=new World(16,16,16,9,false);
        world.set(5,5,5,Block.STONE);
        RaycastHit hit=world.cast(1.5,5.5,5.5,1,0,0,10,new RaycastHit());
        check(hit.hit && hit.x==5 && hit.y==5 && hit.z==5,"射线应命中指定石块");
        check(hit.normalX==-1 && Math.abs(hit.distance-3.5)<1e-6,"命中面法线或距离错误");
        pass("Voxel DDA hit, distance and normal");
    }

    private static void testShrineExists() {
        World world=new World(64,40,64,88,true);
        boolean found=false;
        for(int y=0;y<world.height;y++) if(world.get(world.shrineX,y,world.shrineZ)==Block.MYSTERY)found=true;
        check(found,"世界应包含隐藏星愿方块");
        pass("Hidden shrine");
    }

    private static void testRecipes() {
        Inventory inventory=new Inventory(false);
        Player player=new Player(1,2,1);
        inventory.add(Block.IRON_ORE,3);inventory.add(Block.WOOD,2);
        Recipe iron=inventory.recipes().stream().filter(r->r.toolTier==2).findFirst().orElseThrow();
        check(iron.craft(inventory,player),"材料充足时铁镐应可合成");
        check(player.toolTier==2 && inventory.count(Block.IRON_ORE)==0,"合成应升级工具并扣除材料");
        check(!iron.craft(inventory,player),"同一永久升级不能重复合成");
        pass("Crafting and tool upgrades");
    }

    private static void testSaveRoundTrip() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-test-");Path file=dir.resolve("roundtrip.mw3d");
        try{
            World world=new World(24,20,24,777,true);
            Player player=new Player(12.5,19.25,11.5);player.yaw=.7;player.pitch=-.2;player.health=13;player.toolTier=2;player.flying=true;
            Inventory inventory=new Inventory(false);inventory.add(Block.GOLD_ORE,7);inventory.selected=5;
            world.set(3,7,4,Block.GLOW);
            SaveSystem.save(file,world,player,inventory,.71,true,true);
            SaveSystem.Snapshot s=SaveSystem.load(file);
            check(s.world.seed==777 && s.world.get(3,7,4)==Block.GLOW,"存档应保留世界数据");
            check(Math.abs(s.x-12.5)<1e-9 && s.health==13 && s.toolTier==2 && s.flying,"存档应保留玩家状态");
            check(s.counts[Block.GOLD_ORE.id]==7 && s.selected==5 && s.rainy && s.secretFound,"存档应保留背包与事件状态");
            pass("Version 3 save round-trip");
        }finally{cleanup(dir);}
    }

    private static void testRendererSmoke() {
        World world=new World(48,32,48,31337,true);
        int sx=world.width/2,sz=world.depth/2;
        Player player=new Player(sx+.5,world.surfaceY(sx,sz)+1.02,sz+.5);
        player.yaw=.75;player.pitch=-.12;
        Renderer renderer=new Renderer(world);
        EntitySystem entities=new EntitySystem(world,false);
        renderer.render(player,.26,false,new RaycastHit(),entities.creatures(),0);
        long start=System.nanoTime();
        for(int i=0;i<3;i++) renderer.render(player,.26+i*.001,false,new RaycastHit(),entities.creatures(),0);
        long elapsedMs=(System.nanoTime()-start)/1_000_000/3;
        int first=renderer.image().getRGB(0,0),different=0;
        for(int y=0;y<Renderer.INTERNAL_HEIGHT;y+=9)for(int x=0;x<Renderer.INTERNAL_WIDTH;x+=9)
            if(renderer.image().getRGB(x,y)!=first)different++;
        check(different>100,"渲染结果不应是单色空帧");
        check(elapsedMs<5000,"单帧渲染耗时异常: "+elapsedMs+"ms");
        pass("3D rendering smoke, warmed frame "+elapsedMs+" ms");
    }

    private static void testMineableOres() {
        World w=new World(72,40,72,42042,true);
        int iron=0,gold=0,coal=0;
        for(int z=0;z<w.depth;z++)for(int x=0;x<w.width;x++)for(int y=1;y<12;y++){
            Block b=w.get(x,y,z);
            if(b==Block.IRON_ORE)iron++;
            if(b==Block.GOLD_ORE && (x!=w.shrineX || z!=w.shrineZ))gold++;
            if(b==Block.COAL_ORE)coal++;
        }
        check(iron>50&&gold>30&&coal>100,"三种矿石必须在地下实际生成");
        pass("Underground coal, iron and gold distribution");
    }

    private static void testSpawnAndDimensions() {
        for(int seed=0;seed<12;seed++){
            World w=new World(32,32,32,seed,true);Player p=new Player(0,0,0);p.respawn(w);
            check(!p.collides(w,p.x,p.y,p.z),"出生点应有完整玩家空间");
            check(w.get((int)p.x,(int)p.y,(int)p.z)==Block.AIR,"出生点不得在水中");
        }
        expectIllegal(()->new World(-1,20,20,1,false));
        expectIllegal(()->new World(256,129,256,1,false));
        new EntitySystem(new World(8,8,8,1,false),false);
        pass("Dry spawn across 12 seeds and bounded dimensions");
    }

    private static void testSurfaceCache() {
        World w=new World(16,16,16,1,false);
        w.set(3,3,3,Block.STONE);check(w.surfaceY(3,3)==3,"基础地表高度");
        w.set(3,8,3,Block.GLASS);check(w.surfaceY(3,3)==8,"放置后缓存失效");
        w.set(3,8,3,Block.AIR);check(w.surfaceY(3,3)==3,"挖掘后缓存失效");
        byte[] data=w.copyBlocks();data[(6*w.depth+3)*w.width+3]=(byte)Block.STONE.id;w.replaceBlocks(data);
        check(w.surfaceY(3,3)==6,"恢复存档后缓存失效");
        pass("Surface cache invalidation for build, mine and load");
    }

    private static void testUnderwaterRaycast() {
        World w=new World(16,16,16,1,false);
        w.set(3,5,5,Block.WATER);w.set(4,5,5,Block.WATER);w.set(6,5,5,Block.STONE);
        RaycastHit hit=new RaycastHit();
        w.cast(3.5,5.5,5.5,1,0,0,8,hit);check(hit.block==Block.STONE&&hit.distance>2,"水下不能命中自身水格");
        w.cast(1.5,5.5,5.5,1,0,0,8,hit);check(hit.block==Block.WATER,"岸上应可见水面");
        w.castIgnoringLiquids(1.5,5.5,5.5,1,0,0,8,hit);check(hit.x==6,"采集射线应穿过水格");
        w.cast(1,1,1,0,0,0,8,hit);check(!hit.hit,"零方向射线安全退出");
        pass("Underwater visibility and water-aware mining rays");
    }

    private static void testRaysFromAboveWorld() {
        World w=new World(16,16,16,1,false);w.set(5,8,5,Block.STONE);
        RaycastHit hit=w.cast(5.5,30,5.5,0,-1,0,40,new RaycastHit());
        check(hit.hit&&hit.y==8&&hit.normalY==1,"高空向下射线应进入世界");
        pass("Flight camera rays can re-enter world bounds");
    }

    private static void testHotbar() {
        Inventory i=new Inventory(false);
        for(int slot=0;slot<9;slot++){i.selectHotbarSlot(slot);check(i.selectedBlock()==i.hotbarBlock(slot),"数字键与槽位一致");}
        i.scroll(1);check(i.selected==9,"第二页起点");
        i.selectHotbarSlot(5);check(i.selected==14,"第二页末槽");
        i.selectHotbarSlot(8);check(i.selected==14,"空槽位不应改变选择");
        i.scroll(1);check(i.selected==0,"滚轮应循环完整方块列表");
        pass("Two-page hotbar and exact numeric slot mapping");
    }

    private static void testInventoryBoundaries() {
        Inventory i=new Inventory(false);i.add(Block.WOOD,Integer.MAX_VALUE);
        check(i.count(Block.WOOD)==999,"超大增加数量不会溢出");
        expectIllegal(()->i.add(Block.WOOD,-1));expectIllegal(()->i.remove(Block.WOOD,-1));
        check(!i.remove(Block.WOOD,1000)&&i.count(Block.WOOD)==999,"失败移除不会改变库存");
        int[] values=new int[Block.values().length];values[Block.STONE.id]=50000;i.replaceCounts(values);
        check(i.count(Block.STONE)==999,"恢复数量有上限");
        pass("Inventory overflow, negative amounts and failure atomicity");
    }

    private static void testRecipeCapacity() {
        Inventory i=new Inventory(false);i.add(Block.WOOD,2);i.add(Block.PLANKS,999);Player p=new Player(1,2,1);
        check(!i.recipes().get(0).craft(i,p)&&i.count(Block.WOOD)==2,"容量不足不能消耗材料");
        i.remove(Block.PLANKS,4);check(i.recipes().get(0).craft(i,p)&&i.count(Block.PLANKS)==999,"容量足够可合成");
        pass("Full inventory crafting preserves ingredients");
    }

    private static void testInputCaptureAndFocus() {
        Canvas c=new Canvas();Input i=new Input(c);
        i.mousePressed(new MouseEvent(c,MouseEvent.MOUSE_PRESSED,0,0,5,5,1,false,1));
        check(!i.isCaptured(),"菜单点击不能自行捕获鼠标");
        keyDown(c,i,KeyEvent.VK_W);i.setCaptured(true);
        check(!i.isDown(KeyEvent.VK_W)&&!i.isMouseDown(1),"捕获切换清除残留按键");
        i.mouseMoved(new MouseEvent(c,MouseEvent.MOUSE_MOVED,0,0,50,50,0,false));
        i.mouseMoved(new MouseEvent(c,MouseEvent.MOUSE_MOVED,0,0,57,53,0,false));
        check(i.consumeMouseDX()==7&&i.consumeMouseDY()==3,"无 Robot 回退仍使用相对位移");
        keyDown(c,i,KeyEvent.VK_W);i.focusLost(new FocusEvent(c,FocusEvent.FOCUS_LOST));
        check(!i.isCaptured()&&!i.isDown(KeyEvent.VK_W)&&i.consumeFocusLost(),"失焦释放控制与按键");
        pass("Mouse capture, fallback deltas and focus loss");
    }

    private static void testSwimmingAndCollision() {
        World w=new World(16,16,16,1,false);Canvas c=new Canvas();Input i=new Input(c);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){w.set(x,1,z,Block.STONE);for(int y=2;y<10;y++)w.set(x,y,z,Block.WATER);}
        Player p=new Player(6.5,3,6.5);keyDown(c,i,KeyEvent.VK_SPACE);
        for(int tick=0;tick<60;tick++)p.update(w,i,1.0/120);
        check(p.y>4.5,"持续按住空格应连续游泳上升");
        w.set(6,5,6,Block.STONE);check(p.collides(w,6.5,5,6.5),"固体碰撞检测");
        check(!p.collides(w,8.5,3,8.5),"水不会产生实体碰撞");
        pass("Sustained swimming and solid/water collision");
    }

    private static void testCompanionAndDrops() {
        World w=new World(16,16,16,1,false);EntitySystem e=new EntitySystem(w,true);
        long count=e.creatures().stream().filter(c->c.kind==Creature.Kind.COMPANION).count();
        e.unlockCompanion(1,2,1);check(count==1&&e.creatures().stream().filter(c->c.kind==Creature.Kind.COMPANION).count()==1,"伙伴不重复");
        Creature slime=new Creature(Creature.Kind.SLIME,5,2,5,0);Inventory i=new Inventory(false);
        e.attack(slime,i);e.attack(slime,i);e.attack(slime,i);int loot=i.count(Block.COAL_ORE)+i.count(Block.GLOW);
        check(!e.attack(slime,i)&&loot==1&&i.count(Block.COAL_ORE)+i.count(Block.GLOW)==1,"死亡生物不能重复掉落");
        pass("Restored companion uniqueness and one-time combat loot");
    }

    private static void testSaveValidationAndBackups() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-validation-");Path f=dir.resolve("world.mw3d");
        try{
            World w=new World(16,16,16,1,false);Player p=new Player(5.5,3,5.5);Inventory i=new Inventory(false);
            SaveSystem.save(f,w,p,i,.25,false,false);byte[] first=Files.readAllBytes(f);
            p.health=14;SaveSystem.save(f,w,p,i,.25,false,false);
            check(Arrays.equals(first,Files.readAllBytes(SaveSystem.backupPath(f))),"保存保留上一版");
            Path archive=SaveSystem.archiveBeforeNewWorld(f);check(Arrays.equals(Files.readAllBytes(f),Files.readAllBytes(archive)),"新建前完整备份");
            byte[] valid=Files.readAllBytes(f);byte[] broken=valid.clone();broken[100]=1;Files.write(f,broken);
            expectIo(()->SaveSystem.load(f));expectIo(()->SaveSystem.save(f,w,p,i,.25,false,false));
            check(Arrays.equals(broken,Files.readAllBytes(f)),"损坏原档不会被覆盖");
            Files.write(f,valid);p.x=Double.NaN;expectIo(()->SaveSystem.save(f,w,p,i,.25,false,false));
            check(Arrays.equals(valid,Files.readAllBytes(f)),"非法玩家状态不改原档");
            byte[] unknown=valid.clone();unknown[32]=(byte)255;Files.write(f,unknown);expectIo(()->SaveSystem.load(f));
            byte[] badSize=valid.clone();ByteBuffer.wrap(badSize).putInt(16,-1);Files.write(f,badSize);expectIo(()->SaveSystem.load(f));
            Files.write(f,Arrays.copyOf(valid,valid.length-16));expectIo(()->SaveSystem.load(f));
            pass("Checksums, backups, invalid values, unknown IDs and truncation");
        }finally{cleanup(dir);}
    }

    private static void testLegacyAndArchivedSave() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-legacy-");Path f=dir.resolve("world.mw3d");
        try{
            World w=new World(16,16,16,1,false);Player p=new Player(5,3,5);
            SaveSystem.save(f,w,p,new Inventory(true),.2,false,true);
            byte[] legacy=Files.readAllBytes(f);legacy=Arrays.copyOf(legacy,legacy.length-8);ByteBuffer.wrap(legacy).putInt(4,2);
            Files.write(f,legacy);check(SaveSystem.load(f).secretFound,"版本 2 兼容");
            ByteBuffer.wrap(legacy).putInt(4,1);Files.write(f,legacy);check(SaveSystem.load(f).health==20,"版本 1 兼容");
            SaveSystem.Snapshot archived=SaveSystem.load(Path.of("archive","saves","world.mw3d"));
            check(archived.world.width==112&&archived.world.depth==112,"实际旧玩家存档可恢复");
            pass("Version 1/2 compatibility and actual archived player world");
        }finally{cleanup(dir);}
    }

    private static void testGameTransitionsAndWorldBackup() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-state-");Path file=dir.resolve("world.mw3d");
        try{
            Game g=new Game(file);Input input=(Input)field(g,"input");
            input.mousePressed(new MouseEvent(g,MouseEvent.MOUSE_PRESSED,0,0,2,2,1,false,1));tick(g);
            check(screen(g).equals("MENU")&&!input.isCaptured(),"菜单保持未捕获");
            keyDown(g,input,KeyEvent.VK_ENTER);tick(g);check(screen(g).equals("PLAYING")&&input.isCaptured(),"进入游戏");
            keyDown(g,input,KeyEvent.VK_E);tick(g);check(screen(g).equals("INVENTORY")&&!input.isCaptured(),"打开背包");
            input.mousePressed(new MouseEvent(g,MouseEvent.MOUSE_PRESSED,0,0,2,2,1,false,1));tick(g);
            check(!input.isCaptured(),"背包点击保持释放");
            keyDown(g,input,KeyEvent.VK_ESCAPE);tick(g);check(screen(g).equals("PLAYING"),"背包返回");
            input.focusLost(new FocusEvent(g,FocusEvent.FOCUS_LOST));tick(g);check(screen(g).equals("PAUSED"),"失焦自动暂停");
            keyDown(g,input,KeyEvent.VK_F5);tick(g);check(Files.isRegularFile(file),"暂停时可手动保存");
            byte[] saved=Files.readAllBytes(file);
            Game menu=new Game(file);Input menuInput=(Input)field(menu,"input");
            keyDown(menu,menuInput,KeyEvent.VK_N);tick(menu);
            try(var files=Files.list(dir)){
                Path backup=files.filter(p->p.getFileName().toString().startsWith("world-before-new-")).findFirst().orElseThrow();
                check(Arrays.equals(saved,Files.readAllBytes(backup)),"新建世界保护旧存档");
            }
            check(screen(menu).equals("PLAYING")&&!((Boolean)field(menu,"secretFound")),"新世界状态正确重置");
            menu.shutdown();
            pass("Menus, inventory, focus pause, F5 and new-world backup");
        }finally{cleanup(dir);}
    }

    private static void testRecoveryFromBackup() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-recovery-");Path file=dir.resolve("world.mw3d");
        try{
            World w=new World(16,16,16,99,false);Player p=new Player(5,3,5);Inventory i=new Inventory(false);
            SaveSystem.save(file,w,p,i,.2,false,true);SaveSystem.save(file,w,p,i,.3,true,true);
            Files.write(file,new byte[]{1,2,3});Game g=new Game(file);
            check(((World)field(g,"world")).seed==99&&(Boolean)field(g,"secretFound"),"主档损坏从备份恢复");
            try(var files=Files.list(dir)){check(files.anyMatch(f->f.getFileName().toString().startsWith("world-broken-")),"坏档保留");}
            pass("Corrupt primary recovery preserves broken original");
        }finally{cleanup(dir);}
    }

    private static void testSelectedFaceOutline() {
        World w=new World(16,16,16,1,false);
        for(int y=3;y<8;y++)for(int x=2;x<12;x++)w.set(x,y,8,Block.STONE);
        Player p=new Player(5.5,4,4.5);Renderer r=new Renderer(w);
        RaycastHit target=w.cast(p.x,p.cameraY(),p.z,p.lookX(),p.lookY(),p.lookZ(),6,new RaycastHit());
        r.render(p,.25,false,null,List.of(),0);int a=r.image().getRGB(240,135)&0xFFFFFF;
        r.render(p,.25,false,target,List.of(),0);int b=r.image().getRGB(240,135)&0xFFFFFF;
        check(b!=a&&((b>>16)&255)<180,"选中方块面中央不应整面漂白");
        pass("Target outline uses face coordinates, not plane boundary");
    }

    private static void testMiningAndPlacement() throws Exception {
        Path dir=Files.createTempDirectory("mw3d-build-");Path file=dir.resolve("world.mw3d");
        try{
            World w=new World(16,16,16,1,false);w.set(5,3,8,Block.STONE);
            Player p=new Player(5.5,2,5.5);p.flying=true;Inventory starter=new Inventory(true);
            SaveSystem.save(file,w,p,starter,.25,false,false);Game g=new Game(file);
            Input i=(Input)field(g,"input");keyDown(g,i,KeyEvent.VK_ENTER);tick(g);
            ((EntitySystem)field(g,"entities")).creatures().clear();tick(g);
            i.mousePressed(new MouseEvent(g,MouseEvent.MOUSE_PRESSED,0,0,2,2,1,false,3));tick(g);
            World active=(World)field(g,"world");Inventory bag=(Inventory)field(g,"inventory");
            check(active.get(5,3,7)==Block.DIRT&&bag.count(Block.DIRT)==27,"放置消耗一个物品");
            i.mouseReleased(new MouseEvent(g,MouseEvent.MOUSE_RELEASED,0,0,2,2,1,false,3));
            i.mousePressed(new MouseEvent(g,MouseEvent.MOUSE_PRESSED,0,0,2,2,1,false,1));
            for(int n=0;n<65;n++)tick(g);
            check(active.get(5,3,7)==Block.AIR&&bag.count(Block.DIRT)==28,"长按采集并收回掉落");
            i.mouseReleased(new MouseEvent(g,MouseEvent.MOUSE_RELEASED,0,0,2,2,1,false,1));
            Creature slime=new Creature(Creature.Kind.SLIME,5.5,3.12,7,0);
            ((EntitySystem)field(g,"entities")).creatures().add(slime);
            i.mousePressed(new MouseEvent(g,MouseEvent.MOUSE_PRESSED,0,0,2,2,1,false,1));
            for(int n=0;n<100;n++)tick(g);
            check(slime.health<=0,"长按攻击可连续打败史莱姆");
            ((Player)field(g,"player")).pitch=-1.48;
            ((EntitySystem)field(g,"entities")).creatures().clear();
            int stoneBefore=bag.count(Block.STONE);
            for(int n=0;n<240;n++)tick(g);
            check(bag.count(Block.STONE)==stoneBefore,"世界底边界不能无限刷石头");
            pass("Integrated placement, held mining/combat and unmineable boundary");
        }finally{cleanup(dir);}
    }

    private static Object field(Object obj,String name) throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private static String screen(Game g) throws Exception {return field(g,"screen").toString();}
    private static void tick(Game g) throws Exception {Method m=Game.class.getDeclaredMethod("update",double.class);m.setAccessible(true);m.invoke(g,1.0/120);}
    private static void keyDown(Canvas c,Input input,int key){input.keyPressed(new KeyEvent(c,KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));}
    @FunctionalInterface private interface CheckedAction {void run() throws Exception;}
    private static void expectIo(CheckedAction action) throws Exception {try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected IOException");}
    private static void expectIllegal(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected IllegalArgumentException");}
    private static void cleanup(Path dir) throws IOException {
        try(var files=Files.walk(dir)){for(Path file:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
    }

    private static void pass(String name){passed++;System.out.println("[PASS] "+name);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
