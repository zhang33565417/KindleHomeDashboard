package com.example.kindleboard;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A single versioned package binds pixels, paper patches and touch geometry. */
final class KindleFrame {
    static final int WIDTH = 1072, HEIGHT = 1448;
    record Info(String layout, int rotation, int sourceWidth, int sourceHeight) {
        static Info of(int width, int height) {
            return new Info(width > height ? "landscape-v1" : "portrait-v1", width > height ? 90 : 0, width, height);
        }
    }
    record Bundle(String version, Info info, byte[] bytes) {}

    static Bundle create(String version, byte[] png, PngRenderer renderer) throws IOException {
        return create(version, png, renderer, false);
    }
    static Bundle create(String version, byte[] png, PngRenderer renderer, boolean contentOnly) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
        if (source == null) throw new IOException("Invalid published PNG");
        Info info = Info.of(source.getWidth(), source.getHeight());
        boolean wide = info.rotation() == 90;
        int logicalWidth = wide ? HEIGHT : WIDTH, logicalHeight = wide ? WIDTH : HEIGHT;
        double fit = Math.min(logicalWidth / (double) source.getWidth(), logicalHeight / (double) source.getHeight());
        double fx = (logicalWidth - source.getWidth() * fit) / 2, fy = (logicalHeight - source.getHeight() * fit) / 2;
        BufferedImage frame = physical(fit(source, logicalWidth, logicalHeight, fit, fx, fy), wide);
        BufferedImage paper = physical(fit(renderer.background(source.getWidth(), source.getHeight()), logicalWidth, logicalHeight, fit, fx, fy), wide);
        int tw = wide ? 1457 : 1080, th = wide ? 1079 : 1456;
        double templateScale = Math.min(source.getWidth() / (double) tw, source.getHeight() / (double) th);
        double scale = templateScale * fit;
        double dx = fx + (source.getWidth() - tw * templateScale) / 2 * fit;
        double dy = fy + (source.getHeight() - th * templateScale) / 2 * fit;
        Rectangle clock = physical(rect(wide ? 1190 : 847, 119, wide ? 228 : 193, wide ? 80 : 64, scale, dx, dy), wide, logicalHeight);
        Rectangle clockBackground = grow(clock, 2);
        Rectangle battery = physical(rect(wide ? 830 : 557, wide ? 34 : 207, 130, 30, scale, dx, dy), wide, logicalHeight);
        Rectangle home = physical(rect(22, 10, 104, 76, scale, dx, dy), wide, logicalHeight);
        home = bounded(new Rectangle(home.x - Math.max(0, 70-home.width)/2, home.y - Math.max(0, 70-home.height)/2,
                Math.max(home.width,70), Math.max(home.height,70)));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema", "1"); values.put("version", version); values.put("layout", info.layout());
        values.put("rotation", Integer.toString(info.rotation()));
        values.put("width", Integer.toString(WIDTH)); values.put("height", Integer.toString(HEIGHT));
        values.put("source_width", Integer.toString(source.getWidth())); values.put("source_height", Integer.toString(source.getHeight()));
        putRect(values, "clock", clock); putRect(values, "clock_bg", clockBackground);
        putRect(values, "battery", battery); putRect(values, "home", home);
        if (contentOnly) {
            Graphics2D g = frame.createGraphics();
            try {
                g.drawImage(paper.getSubimage(clockBackground.x,clockBackground.y,clockBackground.width,clockBackground.height),clockBackground.x,clockBackground.y,null);
                g.drawImage(paper.getSubimage(battery.x,battery.y,battery.width,battery.height),battery.x,battery.y,null);
            } finally { g.dispose(); }
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                digest.update(encode(frame));
                digest.update(encode(paper));
                values.put("version", "0");
                digest.update(values.toString().getBytes(StandardCharsets.US_ASCII));
                byte[] hash = digest.digest();
                long number = 0;
                for (int i=0;i<7;i++) number=(number<<8)|(hash[i]&255L);
                version=Long.toString(number == 0 ? 1 : number);
                values.put("version",version);
            } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
        }
        StringBuilder manifest = new StringBuilder(); values.forEach((key,value) -> manifest.append(key).append('=').append(value).append('\n'));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(1);
            entry(zip, "manifest.txt", manifest.toString().getBytes(StandardCharsets.US_ASCII));
            entry(zip, "frame.png", encode(frame));
            entry(zip, "clock-background.png", encode(paper.getSubimage(clockBackground.x, clockBackground.y, clockBackground.width, clockBackground.height)));
            entry(zip, "battery-background.png", encode(paper.getSubimage(battery.x, battery.y, battery.width, battery.height)));
        }
        return new Bundle(version, info, bytes.toByteArray());
    }

    private static BufferedImage fit(BufferedImage source, int width, int height, double scale, double x, double y) {
        BufferedImage target = new BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0,0,width,height);
            g.translate(x,y); g.scale(scale,scale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source,0,0,null);
        } finally { g.dispose(); }
        return target;
    }
    private static BufferedImage physical(BufferedImage source, boolean wide) {
        if (!wide) return source;
        BufferedImage target = new BufferedImage(source.getHeight(),source.getWidth(),BufferedImage.TYPE_BYTE_GRAY);
        for (int y=0;y<source.getHeight();y++) for (int x=0;x<source.getWidth();x++)
            target.getRaster().setSample(source.getHeight()-1-y,x,0,source.getRaster().getSample(x,y,0));
        return target;
    }
    private static Rectangle rect(int x,int y,int w,int h,double scale,double dx,double dy) {
        int left=(int)Math.floor(dx+x*scale), top=(int)Math.floor(dy+y*scale);
        return new Rectangle(left,top,(int)Math.ceil(dx+(x+w)*scale)-left,(int)Math.ceil(dy+(y+h)*scale)-top);
    }
    private static Rectangle physical(Rectangle r,boolean wide,int logicalHeight) {
        return wide ? new Rectangle(logicalHeight-r.y-r.height,r.x,r.height,r.width) : r;
    }
    private static Rectangle bounded(Rectangle r) { return r.intersection(new Rectangle(0,0,WIDTH,HEIGHT)); }
    private static Rectangle grow(Rectangle r,int padding) { Rectangle result=new Rectangle(r); result.grow(padding,padding); return bounded(result); }
    private static void putRect(Map<String,String> values,String name,Rectangle r) {
        values.put(name+"_x",Integer.toString(r.x)); values.put(name+"_y",Integer.toString(r.y));
        values.put(name+"_w",Integer.toString(r.width)); values.put(name+"_h",Integer.toString(r.height));
    }
    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        if (!ImageIO.write(image,"png",output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
}
