package com.myworld3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

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
        System.out.println("全部测试通过：" + passed + " 项 (OK)");
    }

    private static void testDeterministicWorld() {
        World a = new World(48,32,48,123456L,true);
        World b = new World(48,32,48,123456L,true);
        check(Arrays.equals(a.copyBlocks(), b.copyBlocks()), "相同种子应生成相同世界");
        int centerY=a.surfaceY(24,24);
        check(a.get(24,centerY,24).solid,"出生点脚下必须是实体地面");
        pass("世界生成可复现");
    }

    private static void testRaycast() {
        World world=new World(16,16,16,9,false);
        world.set(5,5,5,Block.STONE);
        RaycastHit hit=world.cast(1.5,5.5,5.5,1,0,0,10,new RaycastHit());
        check(hit.hit && hit.x==5 && hit.y==5 && hit.z==5,"射线应命中指定石块");
        check(hit.normalX==-1 && Math.abs(hit.distance-3.5)<1e-6,"命中面法线或距离错误");
        pass("体素 DDA 射线命中");
    }

    private static void testShrineExists() {
        World world=new World(64,40,64,88,true);
        boolean found=false;
        for(int y=0;y<world.height;y++) if(world.get(world.shrineX,y,world.shrineZ)==Block.MYSTERY)found=true;
        check(found,"世界应包含隐藏星愿方块");
        pass("隐藏遗迹生成");
    }

    private static void testRecipes() {
        Inventory inventory=new Inventory(false);
        Player player=new Player(1,2,1);
        inventory.add(Block.IRON_ORE,3);inventory.add(Block.WOOD,2);
        Recipe iron=inventory.recipes().stream().filter(r->r.toolTier==2).findFirst().orElseThrow();
        check(iron.craft(inventory,player),"材料充足时铁镐应可合成");
        check(player.toolTier==2 && inventory.count(Block.IRON_ORE)==0,"合成应升级工具并扣除材料");
        check(!iron.craft(inventory,player),"同一永久升级不能重复合成");
        pass("合成与工具升级");
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
            pass("存档完整往返");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
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
        pass("软件 3D 渲染冒烟（"+elapsedMs+"ms）");
    }

    private static void pass(String name){passed++;System.out.println("[PASS] "+name);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
