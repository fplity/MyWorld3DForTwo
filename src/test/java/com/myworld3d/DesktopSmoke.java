package com.myworld3d;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Isolated headful smoke test. Events go through the real input listeners; screenshots use AWT Robot. */
public final class DesktopSmoke {
    private static JFrame frame;
    private static Game game;
    private static Input input;

    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("mw3d-desktop-");
        Path evidence=Path.of(args.length>0?args[0]:"out/desktop-evidence");Files.createDirectories(evidence);
        try{
            SwingUtilities.invokeAndWait(()->{
                game=new Game(dir.resolve("world.mw3d"));frame=new JFrame("MyWorld3D isolated smoke test");
                frame.add(game);frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);game.start();
            });
            input=(Input)field(game,"input");
            Thread.sleep(650);capture(evidence.resolve("menu-1280.png"));
            Robot mouse=new Robot();
            Point menuPoint=game.getLocationOnScreen();
            mouse.mouseMove(menuPoint.x+game.getWidth()/2,menuPoint.y+game.getHeight()/2);
            mouse.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            mouse.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(150);
            press(KeyEvent.VK_ENTER);await("PLAYING");Thread.sleep(650);
            capture(evidence.resolve("playing-1280.png"));
            double oldYaw=((Player)field(game,"player")).yaw;
            Point center=game.getLocationOnScreen();
            mouse.mouseMove(center.x+game.getWidth()/2+45,center.y+game.getHeight()/2);
            Thread.sleep(180);
            check(Math.abs(((Player)field(game,"player")).yaw-oldYaw)>.015,
                    "physical mouse movement changes camera; state="+field(game,"screen")+", captured="+input.isCaptured()+", focus="+game.hasFocus());
            Point cursor=java.awt.MouseInfo.getPointerInfo().getLocation();
            check(Math.abs(cursor.x-center.x-game.getWidth()/2)<3,"mouse re-centers after physical movement");
            press(KeyEvent.VK_E);await("INVENTORY");
            SwingUtilities.invokeAndWait(()->input.mousePressed(new MouseEvent(game,MouseEvent.MOUSE_PRESSED,0,0,20,20,1,false,1)));
            check(!input.isCaptured(),"inventory click does not capture mouse");
            press(KeyEvent.VK_ENTER);Thread.sleep(100);
            check(((Inventory)field(game,"inventory")).count(Block.WOOD)==7,"workbench crafts through game loop");
            SwingUtilities.invokeAndWait(()->frame.setSize(960,600));Thread.sleep(300);
            capture(evidence.resolve("inventory-960.png"));
            press(KeyEvent.VK_ESCAPE);await("PLAYING");
            SwingUtilities.invokeAndWait(()->input.focusLost(new FocusEvent(game,FocusEvent.FOCUS_LOST)));await("PAUSED");
            press(KeyEvent.VK_F5);Thread.sleep(250);
            check(Files.isRegularFile(dir.resolve("world.mw3d")),"paused F5 writes save");
            capture(evidence.resolve("pause-960.png"));
            game.shutdown();
            SaveSystem.Snapshot saved=SaveSystem.load(dir.resolve("world.mw3d"));
            check(saved.counts[Block.WOOD.id]==7,"shutdown waits for final world snapshot");
            System.out.println("[PASS] Headful menu, camera, inventory click, crafting, 960x600 layout, focus pause, F5 and shutdown save");
            System.out.println("Evidence: "+evidence.toAbsolutePath());
        }finally{
            if(game!=null)game.shutdown();
            SwingUtilities.invokeAndWait(()->{if(frame!=null)frame.dispose();});
            try(var files=Files.walk(dir)){for(Path file:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
        }
    }

    private static void press(int key) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            input.keyPressed(new KeyEvent(game,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,key,KeyEvent.CHAR_UNDEFINED));
            input.keyReleased(new KeyEvent(game,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,key,KeyEvent.CHAR_UNDEFINED));
        });
    }
    private static void await(String state) throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline){if(field(game,"screen").toString().equals(state))return;Thread.sleep(10);}
        throw new AssertionError("Expected "+state+", actual "+field(game,"screen"));
    }
    private static void capture(Path file) throws Exception {
        Point p=game.getLocationOnScreen();ImageIO.write(new Robot().createScreenCapture(new Rectangle(p.x,p.y,game.getWidth(),game.getHeight())),"png",file.toFile());
    }
    private static Object field(Object obj,String name) throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
