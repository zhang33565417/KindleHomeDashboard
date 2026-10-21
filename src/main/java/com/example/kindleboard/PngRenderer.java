package com.example.kindleboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.nlf.calendar.Solar;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static com.example.kindleboard.Models.*;

/** 参考图固定模板：插画和分区保持原样，Java2D 只在预留位置填入真实内容。 */
@Component
public class PngRenderer {
    private final Font baseFont;
    private final BufferedImage template;
    private final Font numberFont = new Font("Serif", Font.BOLD, 80);
    public final String fontWarning;

    public PngRenderer(@Value("${board.font-path:}") String fontPath) throws Exception {
        Font chosen = null;
        if (!fontPath.isBlank()) chosen = Font.createFont(Font.TRUETYPE_FONT, Path.of(fontPath).toFile());
        if (chosen == null) {
            List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String name : List.of("Noto Serif CJK SC", "SimSun", "Songti SC", "Noto Sans CJK SC", "Microsoft YaHei", "Dialog")) {
                Font candidate = new Font(name, Font.PLAIN, 24);
                if ((installed.contains(name) || name.equals("Dialog")) && candidate.canDisplayUpTo("家庭胎动待设置") == -1) {
                    chosen = candidate;
                    break;
                }
            }
        }
        baseFont = chosen == null ? new Font("Dialog", Font.PLAIN, 24) : chosen;
        fontWarning = baseFont.canDisplayUpTo("家庭胎动待设置") == -1 ? "" : "缺少中文字体，请安装 Noto Sans CJK 或设置 BOARD_FONT_PATH 后重启";
        try (var resource = PngRenderer.class.getResourceAsStream("/templates/family-board.png")) {
            if (resource == null) throw new IllegalStateException("缺少家庭看板固定底图");
            template = ImageIO.read(resource);
        }
    }

    /** 先完整编码到临时 PNG，成功才替换旧图；异常交给服务向页面报告。 */
    public void render(Path target, Settings settings, Layout layout, List<Todo> todos, List<Session> sessions,
                       Weather weather, Instant now) throws Exception {
        if (!fontWarning.isEmpty()) throw new IllegalStateException(fontWarning);
        BufferedImage image = new BufferedImage(settings.canvasWidth(), settings.canvasHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 使用原图1086×1448坐标；只等比缩放整图，模块坐标、字号和尺寸不再取自旧网格。
            double scale = Math.min(image.getWidth() / 1086d, image.getHeight() / 1448d);
            graphics.translate((image.getWidth() - 1086 * scale) / 2, (image.getHeight() - 1448 * scale) / 2);
            graphics.scale(scale, scale);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(template, 0, 0, 1086, 1448, null);
            drawFixed(graphics, settings, layout, todos, sessions, weather, now);
        } finally { graphics.dispose(); }
        Path temporary = target.resolveSibling("dashboard.tmp.png");
        if (!ImageIO.write(image, "png", temporary.toFile())) throw new IllegalStateException("PNG 编码失败");
        JsonStore.replace(temporary, target);
    }

    /** 预留区域逐项填字，任何文案只允许在自己的矩形内换行/省略。 */
    private void drawFixed(Graphics2D g, Settings settings, Layout layout, List<Todo> todos, List<Session> sessions, Weather weather, Instant now) {
        ZoneId zone = ZoneId.of(settings.timezone());
        LocalDate today = now.atZone(zone).toLocalDate();
        String heading = settings.title().equals("家里的每一天") ? "家庭信息看板" : settings.title();
        if (!heading.equals("家庭信息看板")) { clear(g, 102, 24, 545, 44); line(g, heading, 106, 24, 535, 44, 36, true); }
        headline(g, today.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), 49, 114, 622, 77, true);
        line(g, today.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)) + "   " + lunarDate(today), 52, 201, 630, 43, 30, false);
        headline(g, now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), 844, 119, 195, 66, false);
        drawWeather(g, settings, weather);
        drawPregnancy(g, settings, today);
        drawTodos(g, todos.stream().filter(t -> t.date().equals(today)).sorted(java.util.Comparator.comparingInt(Todo::sort)).toList());
        drawMovements(g, sessions, today, zone);

        List<CardText> contents = FixedContent.read(layout);
        CardText appointment = contents.get(0);
        line(g, appointment.title(), 126, 1052, 249, 35, 24, true);
        line(g, body(appointment, today), 126, 1090, 228, 25, 18, false);
        int[] starts = {113, 480, 809}, widths = {257, 216, 226};
        for (int index=0; index<3; index++) {
            CardText reminder = contents.get(index+1);
            line(g, reminder.title(), starts[index], 1238, widths[index], 32, 22, true);
            block(g, body(reminder, today), starts[index], 1275, widths[index], 29, 17);
        }
        String footer = contents.get(4).text();
        if (!footer.equals("把日子过得安稳一点，等你慢慢来到我们身边。")) {
            clear(g, 280, 1330, 526, 34); line(g, footer, 285, 1330, 517, 34, 22, false);
        }
    }

    /** 真实天气或明确空状态；旧缓存标记时间，图标也随天气码变化。 */
    private void drawWeather(Graphics2D g, Settings settings, Weather weather) {
        boolean available = weather.lastSuccessAt() != null;
        line(g, available ? weather.cityName() : settings.weather().cityName(), 91, 348, 205, 38, 30, false);
        // 底图中的太阳是参考图装饰；先清除再按真实天气码绘图，断网时绝不冒充晴天。
        clear(g, 296, 355, 102, 110);
        g.setColor(new Color(125,125,125)); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (available && weather.code() != null && weather.code() <= 2) {
            g.fillOval(322,389,48,48);
            for (int ray=0; ray<8; ray++) {
                double angle=ray*Math.PI/4;
                g.drawLine(346+(int)(Math.cos(angle)*33),413+(int)(Math.sin(angle)*33),346+(int)(Math.cos(angle)*45),413+(int)(Math.sin(angle)*45));
            }
        } else if (available) {
            g.fillOval(311,405,39,28); g.fillOval(331,391,44,42); g.fillOval(353,406,33,27);
            if (weather.code() >= 51) for (int drop=0;drop<3;drop++) g.drawLine(322+drop*22,443,316+drop*22,454);
        } else line(g, "—", 307, 380, 79, 65, 60, false);
        if (available) rich(g, 50, 398, 237, 98, new Run(decimal(weather.temperature()), numberFont.deriveFont(108f)), new Run("°C", baseFont.deriveFont(Font.BOLD,44f)));
        else line(g, "不可用", 52, 408, 228, 83, 56, true);
        line(g, available ? WeatherClient.description(weather.code()) : "暂无天气数据", 52, 500, 240, 54, 39, true);
        clear(g, 91, 607, 313, 39);
        line(g, available ? "最高 " + decimal(weather.high()) + "°C / 最低 " + decimal(weather.low()) + "°C" : "最高 / 最低 —", 94, 610, 307, 34, 23, false);
        line(g, "湿度 " + (available ? decimal(weather.humidity()) + "%" : "—"), 95, 655, 118, 35, 23, false);
        line(g, available && weather.windSpeed()!=null ? decimal(weather.windSpeed())+" km/h" : "风速 —", 280, 655, 125, 35, 21, false);
    }

    /** 进度、孕周及倒计时都由预产期推导；未设置时不绘制示例百分比。 */
    private void drawPregnancy(Graphics2D g, Settings settings, LocalDate today) {
        long elapsed = settings.dueDate() == null ? -1 : java.time.temporal.ChronoUnit.DAYS.between(settings.dueDate().minusDays(280), today);
        if (elapsed < 0) line(g, settings.dueDate() == null ? "待设置预产期" : "尚未到估算孕期起点", 467, 351, 387, 85, 46, true);
        else rich(g,467,343,389,91,new Run("孕 ",baseFont.deriveFont(Font.BOLD,42f)),new Run(Long.toString(elapsed/7),numberFont.deriveFont(106f)),new Run("周 + ",baseFont.deriveFont(Font.BOLD,36f)),new Run(Long.toString(elapsed%7),numberFont.deriveFont(92f)),new Run("天",baseFont.deriveFont(Font.BOLD,36f)));
        line(g, settings.dueDate() == null ? "请在管理页设置预产期" : countdown(today, settings.dueDate(), "预产期"),467,441,386,44,31,true);
        g.setColor(new Color(218,218,218)); g.fillRoundRect(467,506,316,24,24,24);
        double progress = elapsed < 0 ? 0 : Math.min(1,elapsed/280d);
        if (progress > 0) { g.setColor(new Color(82,82,82)); g.fillRoundRect(467,506,(int)(316*progress),24,24,24); }
        line(g, elapsed < 0 ? "—" : Math.round(progress*100)+"%",792,500,64,39,26,false);
        line(g,"按预产期估算，今天也要好好休息。",467,552,382,34,22,false);
        clear(g,466,602,340,32);
        g.setColor(new Color(165,165,165)); g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10,new float[]{8,7},0)); g.drawLine(467,619,803,619);
        int stage = elapsed < 0 ? -1 : elapsed < 91 ? 0 : elapsed < 196 ? 1 : 2;
        g.setStroke(new BasicStroke(1.8f));
        for (int index=0;index<3;index++) { int x=505+index*129; g.setColor(index == stage ? new Color(70,70,70) : new Color(250,250,250)); g.fillOval(x-8,611,16,16); g.setColor(new Color(70,70,70)); g.drawOval(x-8,611,16,16); }
        if (settings.dueDate() == null) line(g,"待设置",443,842,246,85,59,true);
        else {
            long days=java.time.temporal.ChronoUnit.DAYS.between(today,settings.dueDate());
            if (days>0) rich(g,443,839,244,88,new Run(Long.toString(days),numberFont.deriveFont(98f)),new Run(" 天",baseFont.deriveFont(Font.BOLD,48f)));
            else line(g,days==0 ? "就在今天" : "已过" + -days + "天",443,844,246,77,54,true);
        }
    }

    /** 只填参考图既有四行；超出数量保存在档案中，图片提示到管理页查看。 */
    private void drawTodos(Graphics2D g, List<Todo> todos) {
        if (todos.isEmpty()) line(g,"今日暂无安排",158,803,229,44,25,false);
        for (int index=0;index<Math.min(4,todos.size());index++) {
            Todo todo=todos.get(index); int y=802+index*53;
            line(g,todo.content(),158,y,229,43,25,false);
            if (todo.done()) { g.setColor(new Color(65,65,65)); g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); g.drawLine(60,y+18,66,y+25); g.drawLine(66,y+25,77,y+10); }
        }
        if (todos.size()>4) line(g,"另有 " + (todos.size()-4) + " 项，详见管理页",156,1006,230,16,13,false);
    }

    /** 今日汇总按点击时区归属统计，最近会话区最多展示两段，不输出医学结论。 */
    private void drawMovements(Graphics2D g, List<Session> sessions, LocalDate today, ZoneId zone) {
        List<Session> day=sessions.stream().filter(s -> s.startedAt().atZone(zone).toLocalDate().equals(today) || s.taps().stream().anyMatch(t -> t.at().atZone(zone).toLocalDate().equals(today)))
                .sorted(java.util.Comparator.comparing(Session::startedAt).reversed()).toList();
        Session active=sessions.stream().filter(s -> s.endedAt()==null).findFirst().orElse(null);
        Session recent=day.isEmpty() ? null : day.get(0);
        // 结束只作用于一段会话，今天仍可继续开始新的记录，不能提示为全天结束。
        String status=active!=null ? "正在记录胎动" : day.isEmpty() ? "今日尚未开始计数" : "本次记录已结束";
        line(g,status,831,787,203,37,20,true);
        if (day.isEmpty() && active==null) { g.setColor(new Color(232,232,232)); g.fillOval(790,793,28,28); g.setColor(new Color(125,125,125)); g.setStroke(new BasicStroke(2)); g.drawOval(795,798,18,18); }
        line(g,"今日会话： " + day.size() + " 段",804,844,226,33,22,false);
        line(g,"最近一次： " + (recent==null ? "暂无" : range(recent,today,zone)),804,881,226,32,21,false);
        line(g,(active==null ? "最近会话： " + (recent==null ? "—" : recent.taps().size()+" 次") : "本次胎动： " + active.taps().size()+" 次"),804,918,226,33,22,false);
        line(g,"今日累计： " + countTaps(sessions,today,zone) + " 次",804,955,226,33,22,false);
        // 给小标题留白，遮住底图中穿过文字的虚线，右侧分隔线和记录行保持原位。
        clear(g,738,1006,96,28);
        Graphics2D heading=(Graphics2D)g.create(749,1006,85,28);
        try {
            // 底图虚线中心在Y=1016.5，按字形轮廓居中，避免字体行高中的上下留白影响视觉对齐。
            var glyphs=baseFont.deriveFont(18f).createGlyphVector(heading.getFontRenderContext(),"最近记录");
            var bounds=glyphs.getVisualBounds();
            heading.setColor(Color.BLACK);
            heading.drawGlyphVector(glyphs,0,(float)(10.5-bounds.getCenterY()));
        } finally { heading.dispose(); }
        for (int index=0;index<Math.min(2,day.size());index++) {
            Session session=day.get(index); int y=1037+index*32;
            line(g,range(session,today,zone),787,y,173,25,19,false);
            long count=session.taps().stream().filter(t -> t.at().atZone(zone).toLocalDate().equals(today)).count();
            line(g,count+" 次",976,y,53,25,18,false);
        }
        if (day.isEmpty()) line(g,"暂无记录",789,1037,220,26,19,false);
        line(g,"仅作记录，不作医学判断。",802,1114,229,32,18,false);
    }

    private static String range(Session session, LocalDate today, ZoneId zone) {
        Instant start=session.startedAt();
        if (!start.atZone(zone).toLocalDate().equals(today)) start=session.taps().stream().filter(t -> t.at().atZone(zone).toLocalDate().equals(today)).map(Tap::at).findFirst().orElse(start);
        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        return formatter.format(start)+"–"+(session.endedAt()==null ? "进行中" : formatter.format(session.endedAt()));
    }

    static String lunarDate(LocalDate date) {
        var lunar=Solar.fromYmd(date.getYear(),date.getMonthValue(),date.getDayOfMonth()).getLunar();
        return "农历"+lunar.getMonthInChinese()+"月"+lunar.getDayInChinese();
    }

    private static String body(CardText text, LocalDate today) { return text.targetDate()==null ? text.text() : countdown(today,text.targetDate(),"目标日期"); }
    private static String decimal(Double value) { return value==null ? "—" : java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

    /** 从底图空白纸面取色覆盖旧装饰数据，不触碰边框及插画。 */
    private void clear(Graphics2D g,int x,int y,int width,int height) { g.setPaint(new GradientPaint(x,y,new Color(template.getRGB(440,100)),x,y+height,new Color(template.getRGB(440,240)))); g.fillRect(x,y,width,height); }

    private void line(Graphics2D g,String text,int x,int y,int width,int height,int size,boolean bold) { fitted(g,text,x,y,width,height,baseFont.deriveFont(bold ? Font.BOLD : Font.PLAIN,(float)size)); }
    /** 日期和时钟固定在参考图字框内，中文用宋体、数字用衬线粗体，避免系统字体字宽改变排版。 */
    private void headline(Graphics2D g,String text,int x,int y,int width,int height,boolean chinese) {
        AttributedString attributed=new AttributedString(text);
        attributed.addAttribute(TextAttribute.FONT,chinese ? baseFont.deriveFont(Font.BOLD,100f) : numberFont.deriveFont(100f));
        if (chinese) for (int index=0;index<text.length();index++) if (Character.isDigit(text.charAt(index))) attributed.addAttribute(TextAttribute.FONT,numberFont.deriveFont(100f),index,index+1);
        TextLayout layout=new TextLayout(attributed.getIterator(),g.getFontRenderContext());
        var bounds=layout.getBounds(); Graphics2D part=(Graphics2D)g.create(x,y,width,height);
        try { part.setColor(Color.BLACK); part.scale(width/bounds.getWidth(),height/bounds.getHeight()); layout.draw(part,(float)-bounds.getX(),(float)-bounds.getY()); }
        finally { part.dispose(); }
    }
    private void fitted(Graphics2D g,String text,int x,int y,int width,int height,Font font) {
        text=text.replace('\n',' ').replace('\r',' ');
        Graphics2D part=(Graphics2D)g.create(x,y,width,height);
        try {
            part.setColor(Color.BLACK); part.setFont(font); FontMetrics metrics=part.getFontMetrics();
            float scale=Math.min(1f,Math.min(width/(float)Math.max(1,metrics.stringWidth(text)),height/(float)metrics.getHeight()));
            part.setFont(font.deriveFont(Math.max(12f,font.getSize2D()*scale))); metrics=part.getFontMetrics();
            part.drawString(text,0,(height-metrics.getHeight())/2+metrics.getAscent());
        } finally { part.dispose(); }
    }

    private void block(Graphics2D g,String text,int x,int y,int width,int height,int size) {
        g.setFont(baseFont.deriveFont((float)size)); g.setColor(Color.BLACK); drawText(g,text,x,y,width,height,"left");
    }

    /** 大数字与中文单位共用一条基线，保留参考图的字号层次。 */
    private record Run(/* 字段文字 */ String text, /* 该段字体 */ Font font) {}
    private void rich(Graphics2D g,int x,int y,int width,int height,Run... runs) {
        int total=0; for (Run run:runs) total+=g.getFontMetrics(run.font()).stringWidth(run.text());
        float ratio=Math.min(1,width/(float)Math.max(1,total));
        Graphics2D part=(Graphics2D)g.create(x,y,width,height);
        try {
            part.setColor(Color.BLACK); int offset=0;
            for (Run run:runs) { part.setFont(run.font().deriveFont(run.font().getSize2D()*ratio)); FontMetrics metrics=part.getFontMetrics(); part.drawString(run.text(),offset,height-9); offset+=metrics.stringWidth(run.text()); }
        } finally { part.dispose(); }
    }

    /** 按实际字宽断行，最后一行省略；裁切同时约束水平和垂直边界。 */
    static void drawText(Graphics2D g, String text, int x, int y, int width, int height, String align) {
        if (width <= 0 || height <= 0) return;
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight() + 4, maxLines = height / lineHeight;
        if (maxLines == 0) return;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int point : paragraph.codePoints().toArray()) {
                String character = new String(Character.toChars(point));
                if (!line.isEmpty() && metrics.stringWidth(line + character) > width) {
                    lines.add(line.toString()); line.setLength(0);
                }
                line.append(character);
            }
            lines.add(line.toString());
        }
        Shape original = g.getClip();
        g.clipRect(x, y, width, height);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            String line = lines.get(index);
            if (index == maxLines - 1 && lines.size() > maxLines) {
                while (!line.isEmpty() && metrics.stringWidth(line + "…") > width) line = line.substring(0, line.offsetByCodePoints(line.length(), -1));
                line += "…";
            }
            int offset = switch (align) { case "center" -> (width - metrics.stringWidth(line)) / 2; case "right" -> width - metrics.stringWidth(line); default -> 0; };
            g.drawString(line, x + Math.max(0, offset), y + metrics.getAscent() + index * lineHeight);
        }
        g.setClip(original);
    }

    static boolean sameLocation(Settings settings, Weather weather) {
        return java.util.Objects.equals(weather.latitude(), settings.weather().latitude())
                && java.util.Objects.equals(weather.longitude(), settings.weather().longitude())
                && java.util.Objects.equals(weather.cityName(), settings.weather().cityName());
    }

    static long countTaps(List<Session> sessions, LocalDate date, ZoneId zone) {
        return sessions.stream().flatMap(s -> s.taps().stream()).filter(t -> t.at().atZone(zone).toLocalDate().equals(date)).count();
    }

    static Pregnancy pregnancy(Settings settings, LocalDate today) {
        if (settings.dueDate() == null) return new Pregnancy("待设置", "待设置");
        long elapsed = java.time.temporal.ChronoUnit.DAYS.between(settings.dueDate().minusDays(280), today);
        String weeks = elapsed < 0 ? "尚未到估算孕期起点" : elapsed / 7 + "周" + elapsed % 7 + "天";
        return new Pregnancy(weeks, countdown(today, settings.dueDate(), "预产期"));
    }

    static String countdown(LocalDate today, LocalDate target, String name) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, target);
        return days > 0 ? "距" + name + "还有 " + days + " 天" : days == 0 ? "今天是" + name : "距" + name + "已过 " + -days + " 天";
    }
}
