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
import java.util.List;
import java.util.Locale;
import static com.example.kindleboard.Models.*;

/** 参考图固定模板：插画和分区保持原样，Java2D 只在预留位置填入真实内容。 */
@Component
public class PngRenderer {
    private final Font baseFont;
    private final BufferedImage portraitTemplate;
    private final BufferedImage landscapeTemplate;
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
        portraitTemplate = loadTemplate("family-board-portrait.png");
        landscapeTemplate = loadTemplate("family-board-landscape.png");
    }

    /** 两种方向分别加载用户提供的底图；缺失时明确失败，避免套用错误版式。 */
    private BufferedImage loadTemplate(String name) throws Exception {
        try (var resource = PngRenderer.class.getResourceAsStream("/templates/" + name)) {
            if (resource == null) throw new IllegalStateException("缺少家庭看板底图：" + name);
            BufferedImage image = ImageIO.read(resource);
            if (image == null) throw new IllegalStateException("家庭看板底图无法读取：" + name);
            return image;
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
            // 宽高决定方向，沿用既有设置文件；每个方向使用独立底图与文字坐标。
            boolean landscape = settings.canvasWidth() > settings.canvasHeight();
            int width = landscape ? 1457 : 1080, height = landscape ? 1079 : 1456;
            double scale = Math.min(image.getWidth() / (double) width, image.getHeight() / (double) height);
            graphics.translate((image.getWidth() - width * scale) / 2, (image.getHeight() - height * scale) / 2);
            graphics.scale(scale, scale);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(landscape ? landscapeTemplate : portraitTemplate, 0, 0, width, height, null);
            drawFixed(graphics, settings, layout, todos, sessions, weather, now, landscape);
        } finally { graphics.dispose(); }
        Path temporary = target.resolveSibling("dashboard.tmp.png");
        if (!ImageIO.write(image, "png", temporary.toFile())) throw new IllegalStateException("PNG 编码失败");
        JsonStore.replace(temporary, target);
    }

    /** 各方向只填自己的文字槽位；提醒卡片不再输出，历史内容仍留在档案中。 */
    private void drawFixed(Graphics2D g, Settings settings, Layout layout, List<Todo> todos, List<Session> sessions, Weather weather, Instant now, boolean wide) {
        ZoneId zone = ZoneId.of(settings.timezone());
        LocalDate today = now.atZone(zone).toLocalDate();
        String heading = settings.title().equals("家里的每一天") ? "家庭信息看板" : settings.title();
        if (!heading.equals("家庭信息看板")) {
            clear(g, wide ? 120 : 110, 17, wide ? 620 : 530, 56);
            line(g, heading, wide ? 125 : 115, 17, wide ? 610 : 520, 56, 42, true);
        }
        headline(g, today.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), 48, 114, wide ? 730 : 646, 85, true);
        line(g, today.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)), wide ? 846 : 50, wide ? 116 : 202, wide ? 190 : 145, 40, 34, false);
        line(g, lunarDate(today), wide ? 846 : 194, wide ? 169 : 202, wide ? 205 : 470, 35, 29, false);
        headline(g, now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), wide ? 1190 : 847, 119, wide ? 228 : 193, wide ? 80 : 64, false);
        drawWeather(g, settings, weather, wide);
        drawPregnancy(g, settings, today, wide);
        drawTodos(g, todos.stream().filter(t -> t.date().equals(today)).sorted(java.util.Comparator.comparingInt(Todo::sort)).toList(), wide);
        drawMovements(g, sessions, today, zone, wide);
        List<CardText> contents = FixedContent.read(layout);
        CardText appointment = contents.get(0);
        line(g, appointment.title(), wide ? 148 : 152, wide ? 897 : 1047, wide ? 326 : 347, 35, 29, true);
        line(g, body(appointment, today), wide ? 148 : 152, wide ? 934 : 1088, wide ? 326 : 347, 29, 23, false);
        String footer = contents.get(4).text();
        // 旧默认寄语改用新底图的短句；用户自定义寄语继续显示。
        if (!footer.equals("把日子过得安稳一点，等你慢慢来到我们身边。")) {
            clear(g, wide ? 400 : 270, wide ? 988 : 1361, wide ? 650 : 540, 35);
            line(g, footer, wide ? 405 : 275, wide ? 988 : 1361, wide ? 640 : 530, 35, 25, false);
        }
    }

    /** 天气失败显示空状态；缓存明确标记，避免把旧数据当成当前实况。 */
    private void drawWeather(Graphics2D g, Settings settings, Weather weather, boolean wide) {
        boolean available = weather.lastSuccessAt() != null;
        int x = wide ? 60 : 53, top = wide ? 306 : 350;
        line(g, (available ? weather.cityName() : settings.weather().cityName()) + (available && weather.stale() ? "（缓存）" : ""), x+40, top, wide ? 280 : 210, 40, 32, false);
        if (available) rich(g, x, top+47, 245, 106, new Run(decimal(weather.temperature()),numberFont.deriveFont(120f)),new Run("°C",baseFont.deriveFont(Font.BOLD,48f)));
        else line(g,"不可用",x,top+55,245,96,60,true);
        line(g,available ? WeatherClient.description(weather.code()) : "暂无天气数据",x,top+157,275,54,44,true);
        // 天气符号随真实天气码变化，空状态不保留参考图中的示例太阳。
        int sx=wide ? 445 : 371, sy=wide ? 365 : 410;
        g.setColor(new Color(130,130,130)); g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        if (available && weather.code()!=null && weather.code()<=2) {
            g.fillOval(sx-26,sy-26,52,52);
            for(int ray=0;ray<8;ray++) { double angle=ray*Math.PI/4; g.drawLine(sx+(int)(Math.cos(angle)*36),sy+(int)(Math.sin(angle)*36),sx+(int)(Math.cos(angle)*50),sy+(int)(Math.sin(angle)*50)); }
        } else if (available) {
            g.fillOval(sx-46,sy-5,50,32); g.fillOval(sx-20,sy-25,48,50); g.fillOval(sx+8,sy-4,39,31);
            if(weather.code()!=null && weather.code()>=51) for(int drop=0;drop<3;drop++) g.drawLine(sx-25+drop*24,sy+37,sx-31+drop*24,sy+49);
        } else line(g,"—",sx-30,sy-30,60,60,48,false);
        line(g,available ? "最高 "+decimal(weather.high())+"°C / 最低 "+decimal(weather.low())+"°C" : "最高 / 最低 —",x+44,wide ? 535 : 600,wide ? 405 : 348,36,26,false);
        line(g,"湿度 "+(available ? decimal(weather.humidity())+"%" : "—"),x+44,wide ? 578 : 651,158,36,26,false);
        line(g,available && weather.windSpeed()!=null ? decimal(weather.windSpeed())+" km/h" : "风速 —",wide ? 311 : 310,wide ? 578 : 651,wide ? 220 : 145,36,25,false);
    }

    /** 孕周、进度和宝宝倒计时由同一预产期计算；超期与未设置均不显示示例值。 */
    private void drawPregnancy(Graphics2D g, Settings settings, LocalDate today, boolean wide) {
        long elapsed=settings.dueDate()==null ? -1 : java.time.temporal.ChronoUnit.DAYS.between(settings.dueDate().minusDays(280),today);
        int x=wide ? 617 : 507, y=wide ? 304 : 354, width=wide ? 490 : 345;
        if(elapsed<0) line(g,settings.dueDate()==null ? "待设置预产期" : "尚未到估算孕期起点",x,y,width,88,44,true);
        else rich(g,x,y,width,91,new Run("孕 ",baseFont.deriveFont(Font.BOLD,44f)),new Run(Long.toString(elapsed/7),numberFont.deriveFont(107f)),new Run("周 + ",baseFont.deriveFont(Font.BOLD,38f)),new Run(Long.toString(elapsed%7),numberFont.deriveFont(96f)),new Run("天",baseFont.deriveFont(Font.BOLD,38f)));
        int stage=elapsed<0 ? -1 : elapsed<91 ? 0 : elapsed<196 ? 1 : 2;
        String[] stages={"孕早期","孕中期","孕晚期"};
        line(g,wide ? (stage<0 ? "请在管理页设置预产期" : stages[stage]+" | 按预产期估算") : (settings.dueDate()==null ? "请在管理页设置预产期" : countdown(today,settings.dueDate(),"预产期")),x,y+99,width,41,29,false);
        double progress=elapsed<0 ? 0 : Math.min(1,elapsed/280d);
        int barWidth=width-65, barY=y+158;
        g.setColor(new Color(222,222,222)); g.fillRoundRect(x,barY,barWidth,24,24,24);
        if(progress>0) { g.setColor(new Color(82,82,82)); g.fillRoundRect(x,barY,(int)(barWidth*progress),24,24,24); }
        line(g,elapsed<0 ? "—" : Math.round(progress*100)+"%",x+barWidth+10,barY-6,60,36,26,false);
        if(!wide) line(g,"按预产期估算，今天也要好好休息。",x,y+199,width,34,21,false);
        int stageY=wide ? 528 : 615, stageWidth=wide ? width : 305, gap=(stageWidth-50)/2;
        g.setStroke(new BasicStroke(1.5f)); g.setColor(Color.GRAY); g.drawLine(x,stageY,x+stageWidth,stageY);
        String[] limits={"0–12周","13–27周","28–40周"};
        for(int index=0;index<3;index++) {
            int cx=x+25+index*gap;
            g.setColor(index==stage ? Color.DARK_GRAY : Color.WHITE); g.fillOval(cx-10,stageY-10,20,20);
            g.setColor(Color.DARK_GRAY); g.drawOval(cx-10,stageY-10,20,20);
            line(g,stages[index],cx-32,stageY+18,90,28,22,index==stage);
            line(g,limits[index],cx-32,stageY+49,90,26,20,false);
        }
        int bx=wide ? 928 : 319, by=wide ? 788 : 1203, bw=wide ? 230 : 252;
        if(settings.dueDate()==null) line(g,"待设置",bx,by,bw,90,64,true);
        else {
            long days=java.time.temporal.ChronoUnit.DAYS.between(today,settings.dueDate());
            if(days>0) rich(g,bx,by,bw,94,new Run(Long.toString(days),numberFont.deriveFont(112f)),new Run("天",baseFont.deriveFont(Font.BOLD,54f)));
            else line(g,days==0 ? "就在今天" : "已过"+-days+"天",bx,by,bw,90,54,true);
        }
    }

    /** 两种版式均展示四条安排，完成状态只标记对应勾选框。 */
    private void drawTodos(Graphics2D g, List<Todo> todos, boolean wide) {
        int x=wide ? 178 : 206, y=wide ? 705 : 796, gap=wide ? 43 : 58, width=wide ? 292 : 290;
        if(todos.isEmpty()) line(g,"今日暂无安排",x,y,width,40,29,false);
        for(int index=0;index<Math.min(4,todos.size());index++) {
            Todo todo=todos.get(index); int row=y+index*gap;
            line(g,todo.content(),x,row,width,40,30,false);
            if(todo.done()) { int check=wide ? 59 : 66; g.setColor(Color.DARK_GRAY); g.setStroke(new BasicStroke(3)); g.drawLine(check,row+17,check+7,row+24); g.drawLine(check+7,row+24,check+20,row+6); }
        }
        if(todos.size()>4) line(g,"另有 "+(todos.size()-4)+" 项，详见管理页",x,wide ? 872 : 1008,width,18,15,false);
    }

    /** 今日汇总沿用点击时区，最近两段会话保留；管理页仍可查看完整历史。 */
    private void drawMovements(Graphics2D g, List<Session> sessions, LocalDate today, ZoneId zone, boolean wide) {
        List<Session> day=sessions.stream().filter(s -> s.startedAt().atZone(zone).toLocalDate().equals(today) || s.taps().stream().anyMatch(t -> t.at().atZone(zone).toLocalDate().equals(today)))
                .sorted(java.util.Comparator.comparing(Session::startedAt).reversed()).toList();
        Session active=sessions.stream().filter(s -> s.endedAt()==null).findFirst().orElse(null);
        Session recent=day.isEmpty() ? null : day.get(0);
        int x=wide ? 544 : 579, y=wide ? 704 : 791, width=wide ? 330 : 435, gap=wide ? 40 : 49;
        line(g,"今日记录： "+day.size()+" 次",x,y,width,42,32,true);
        line(g,"最近一次： "+(recent==null ? "暂无" : range(recent,today,zone)),x,y+gap,width,38,28,false);
        line(g,(active==null ? "最近胎动： "+(recent==null ? "—" : recent.taps().size()+" 次") : "本次胎动： "+active.taps().size()+" 次"),x,y+gap*2,width,38,28,false);
        line(g,"今日累计： "+countTaps(sessions,today,zone)+" 次",x,y+gap*3,width,38,28,false);
        int recentY=wide ? 875 : 1011, rowGap=wide ? 30 : 38, rowOffset=wide ? 30 : 35;
        line(g,"最近记录",x,recentY,width,30,25,true);
        for(int index=0;index<Math.min(2,day.size());index++) {
            Session session=day.get(index);
            long count=session.taps().stream().filter(t -> t.at().atZone(zone).toLocalDate().equals(today)).count();
            line(g,range(session,today,zone),x,recentY+rowOffset+index*rowGap,width-72,32,25,false);
            line(g,count+" 次",x+width-68,recentY+rowOffset+index*rowGap,68,32,25,false);
        }
        if(day.isEmpty()) line(g,"暂无记录",x,recentY+35,width,32,25,false);
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

    /** 自定义标题与寄语替换静态文案，覆盖范围限制在预留纸面内。 */
    private void clear(Graphics2D g,int x,int y,int width,int height) { g.setColor(new Color(253,253,252)); g.fillRect(x,y,width,height); }

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
