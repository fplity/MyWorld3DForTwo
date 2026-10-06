package com.myworld3d;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public final class GameLauncher {
    private GameLauncher() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("方块奇境 3D · Java Edition");
            Game game = new Game();
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.add(game);
            frame.pack();
            frame.setMinimumSize(new java.awt.Dimension(960, 600));
            frame.setLocationRelativeTo(null);
            frame.setIconImage(createIcon());
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    game.shutdown();
                    frame.dispose();
                    System.exit(0);
                }
            });
            frame.setVisible(true);
            game.start();
        });
    }

    private static BufferedImage createIcon() {
        BufferedImage icon = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=icon.createGraphics();
        g.setColor(new Color(78,151,66));g.fillRect(5,5,54,22);
        g.setColor(new Color(112,75,46));g.fillRect(5,27,54,32);
        g.setColor(new Color(134,188,92));g.fillRect(5,5,54,7);
        g.setColor(new Color(50,35,25,100));g.drawRect(5,5,53,53);g.dispose();
        return icon;
    }
}
