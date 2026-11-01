import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Match PngRenderer's grayscale conversion and bicubic template transform. */
public class RenderBackground {
    public static void main(String[] args) throws Exception {
        BufferedImage template = ImageIO.read(new File(args[0]));
        int w = Integer.parseInt(args[2]), h = Integer.parseInt(args[3]);
        BufferedImage target = new BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.min(w/(double)template.getWidth(),h/(double)template.getHeight());
            g.translate((w-template.getWidth()*scale)/2,(h-template.getHeight()*scale)/2);
            g.scale(scale,scale);
            g.drawImage(template,0,0,template.getWidth(),template.getHeight(),null);
        } finally { g.dispose(); }
        ImageIO.write(target,"png",new File(args[1]));
    }
}
