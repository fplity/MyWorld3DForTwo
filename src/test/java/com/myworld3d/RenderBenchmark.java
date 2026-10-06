package com.myworld3d;

import java.util.Arrays;

/** Same public API as the original version, so the two builds can be compared. */
public final class RenderBenchmark {
    public static void main(String[] args) {
        World w=new World(31337L);
        Player p=new Player(w.width/2.0+.5,w.surfaceY(w.width/2,w.depth/2)+1.02,w.depth/2.0+.5);
        p.yaw=.75;p.pitch=-.12;
        Renderer r=new Renderer(w);EntitySystem e=new EntitySystem(w,false);RaycastHit target=new RaycastHit();
        for(int i=0;i<15;i++)r.render(p,.26,false,target,e.creatures(),0);
        long[] samples=new long[40];
        for(int i=0;i<samples.length;i++){
            long start=System.nanoTime();r.render(p,.26,false,target,e.creatures(),0);samples[i]=System.nanoTime()-start;
        }
        Arrays.sort(samples);
        System.out.printf(java.util.Locale.ROOT,"scene=seed31337/default112x48x112/res480x270 samples=40 median=%.2fms p95=%.2fms%n",
                samples[samples.length/2]/1e6,samples[37]/1e6);
    }
}
