package com.myworld3d;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

/** Thread-safe keyboard/mouse state with optional first-person mouse capture. */
public final class Input implements KeyListener, MouseListener, MouseMotionListener,
        MouseWheelListener, FocusListener {
    private final Canvas canvas;
    private final boolean[] keys = new boolean[768];
    private final boolean[] pressed = new boolean[768];
    private final boolean[] mouse = new boolean[6];
    private final boolean[] mousePressed = new boolean[6];
    private final Cursor hiddenCursor;
    private final Cursor normalCursor;
    private Robot robot;
    private boolean captured;
    private boolean warping;
    private int mouseDX, mouseDY, wheel;

    public Input(Canvas canvas) {
        this.canvas = canvas;
        this.normalCursor = canvas.getCursor();
        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        this.hiddenCursor = Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "hidden");
        try { robot = new Robot(); } catch (AWTException | SecurityException ignored) { robot = null; }
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addMouseWheelListener(this);
        canvas.addFocusListener(this);
        canvas.setFocusable(true);
    }

    public synchronized boolean isDown(int keyCode) {
        return keyCode >= 0 && keyCode < keys.length && keys[keyCode];
    }

    public synchronized boolean consumePressed(int keyCode) {
        if (keyCode < 0 || keyCode >= pressed.length) return false;
        boolean value = pressed[keyCode];
        pressed[keyCode] = false;
        return value;
    }

    public synchronized boolean isMouseDown(int button) {
        return button >= 0 && button < mouse.length && mouse[button];
    }

    public synchronized boolean consumeMousePressed(int button) {
        if (button < 0 || button >= mousePressed.length) return false;
        boolean value = mousePressed[button];
        mousePressed[button] = false;
        return value;
    }

    public synchronized int consumeMouseDX() { int v = mouseDX; mouseDX = 0; return v; }
    public synchronized int consumeMouseDY() { int v = mouseDY; mouseDY = 0; return v; }
    public synchronized int consumeWheel() { int v = wheel; wheel = 0; return v; }
    public synchronized boolean isCaptured() { return captured; }

    public void setCaptured(boolean value) {
        synchronized (this) {
            captured = value;
            mouseDX = mouseDY = 0;
            for (int i = 0; i < mouse.length; i++) mouse[i] = mousePressed[i] = false;
        }
        canvas.setCursor(value ? hiddenCursor : normalCursor);
        if (value) {
            canvas.requestFocusInWindow();
            recenter();
        }
    }

    private void recenter() {
        if (!captured || robot == null || !canvas.isShowing() || warping) return;
        try {
            Point p = canvas.getLocationOnScreen();
            warping = true;
            robot.mouseMove(p.x + canvas.getWidth() / 2, p.y + canvas.getHeight() / 2);
        } catch (IllegalStateException ignored) { warping = false; }
    }

    @Override public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) {
            if (!keys[code]) pressed[code] = true;
            keys[code] = true;
        }
    }

    @Override public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < keys.length) keys[code] = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    @Override public void mousePressed(MouseEvent e) {
        if (!captured) { setCaptured(true); return; }
        synchronized (this) {
            int b = e.getButton();
            if (b >= 0 && b < mouse.length) { mouse[b] = true; mousePressed[b] = true; }
        }
    }

    @Override public synchronized void mouseReleased(MouseEvent e) {
        int b = e.getButton();
        if (b >= 0 && b < mouse.length) mouse[b] = false;
    }

    @Override public void mouseMoved(MouseEvent e) {
        if (!captured) return;
        int cx = canvas.getWidth() / 2, cy = canvas.getHeight() / 2;
        int dx = e.getX() - cx, dy = e.getY() - cy;
        synchronized (this) {
            if (warping && Math.abs(dx) <= 2 && Math.abs(dy) <= 2) {
                warping = false;
                return;
            }
            mouseDX += dx;
            mouseDY += dy;
        }
        if (robot != null) SwingUtilities.invokeLater(this::recenter);
    }

    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public synchronized void mouseWheelMoved(MouseWheelEvent e) { wheel += e.getWheelRotation(); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void focusGained(FocusEvent e) {}
    @Override public void focusLost(FocusEvent e) { setCaptured(false); clear(); }

    private synchronized void clear() {
        for (int i = 0; i < keys.length; i++) keys[i] = pressed[i] = false;
        for (int i = 0; i < mouse.length; i++) mouse[i] = mousePressed[i] = false;
    }
}
