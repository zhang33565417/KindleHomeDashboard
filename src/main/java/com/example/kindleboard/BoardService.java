package com.example.kindleboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import javax.imageio.ImageIO;
import java.time.*;
import java.util.*;
import static com.example.kindleboard.Models.*;
import static com.example.kindleboard.PngRenderer.*;

/** 简单串行处理文件更新与重绘，不引入数据库、队列或多余服务层。 */
@Service
public class BoardService {
    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    static final Set<String> TYPES = Set.of("DATE", "TIME", "WEATHER", "GESTATION", "BABY_COUNTDOWN", "TODOS", "MOVEMENT", "TEXT", "COUNTDOWN");
    private final JsonStore store;
    private final PngRenderer renderer;
    private final WeatherClient weatherClient;
    private Settings settings;
    private Layout layout;
    private List<Todo> todos;
    private List<Session> sessions;
    private Weather weather;
    private Instant imageAt;
    private int imageWidth, imageHeight;
    private KindleFrame.Bundle kindleCache;
    private Instant weatherAttempt;
    private String renderError = "";

    public BoardService(JsonStore store, PngRenderer renderer, WeatherClient weatherClient) throws IOException {
        this.store = store; this.renderer = renderer; this.weatherClient = weatherClient;
        settings = store.load("settings.json", Settings.class, BoardService::defaultSettings);
        layout = store.load("layout.json", Layout.class, BoardService::defaultLayout);
        todos = new ArrayList<>(Arrays.asList(store.load("todos.json", Todo[].class, () -> new Todo[0])));
        sessions = new ArrayList<>(store.load("movements.json", MovementFile.class, () -> new MovementFile(List.of())).sessions());
        weather = store.load("weather.json", Weather.class, () -> new Weather(null, null, null, null, null, null, null, null, null, null, true, "天气暂不可用", null));
        // 只在全部文件读入和配置校验完成后渲染；异常不会用默认数据覆盖已有档案。
        validateSettings(settings); validateLayout(layout);
        for (Todo todo : todos) validateTodo(todo);
        for (Session session : sessions) {
            require(session != null && session.id() != null && session.startedAt() != null && session.note() != null && session.taps() != null, "胎动文件数据不完整");
            for (Tap tap : session.taps()) require(tap != null && tap.id() != null && tap.at() != null, "胎动事件不完整");
        }
        // 完整解码成功才复用旧图；版本随 PNG 的修改时间持久化，不依赖进程启动时间。
        if (Files.isRegularFile(imagePath())) {
            try {
                var decoded = ImageIO.read(imagePath().toFile());
                if (decoded != null) {
                    imageAt = Files.getLastModifiedTime(imagePath()).toInstant();
                    imageWidth = decoded.getWidth(); imageHeight = decoded.getHeight();
                }
            } catch (IOException e) { log.warn("已有图片无法读取，将重新生成"); }
        }
        if (imageAt == null) render();
    }

    /** 胎动文件保留清晰的 sessions 根节点。 */
    public record MovementFile(/* 所有历史会话 */ List<Session> sessions) {}

    static Settings defaultSettings() {
        return new Settings("家里的每一天", "Asia/Shanghai", 1072, 1448,
                new Location("郑州", 34.7466, 113.6254, 60), null, 60);
    }

    static Layout defaultLayout() {
        return new Layout(12, 24, List.of(
                widget("date", "DATE", 0, 0, 6, 4, "家里的每一天", "", 46),
                widget("weather", "WEATHER", 6, 0, 6, 4, "窗外天气", "", 21),
                widget("gestation", "GESTATION", 0, 4, 6, 4, "与宝宝一起", "", 42),
                widget("baby", "BABY_COUNTDOWN", 6, 4, 6, 4, "期待见面的日子", "", 34),
                widget("movement", "MOVEMENT", 0, 8, 12, 4, "小小的回应 · 胎动记录", "", 31),
                widget("todos", "TODOS", 0, 12, 12, 5, "今日安排", "", 30),
                widget("text", "TEXT", 0, 17, 12, 3, "留给家人的话", "慢慢来，把日子过成喜欢的样子。", 32),
                widget("time", "TIME", 0, 23, 12, 1, "", "", 18)));
    }

    private static Widget widget(String id, String type, int x, int y, int w, int h, String title, String text, int size) {
        return new Widget(id, type, true, x, y, w, h, title, text, null, size, "left", type.equals("TIME") ? 4 : type.equals("WEATHER") ? 14 : 22);
    }

    public synchronized Settings settings() { return settings; }
    public synchronized Layout layout() { return layout; }
    public synchronized List<CardText> content() { return FixedContent.read(layout); }
    public synchronized void saveContent(List<CardText> values) throws IOException { saveLayout(FixedContent.merge(layout, values)); }
    public synchronized Weather weather() { return weather; }
    private LocalDate today() { return LocalDate.now(ZoneId.of(settings.timezone())); }
    Path imagePath() { return store.directory.resolve("dashboard.png"); }

    /** 版本只读取成功发布的图片状态；尚无可用图片时不返回虚假版本，也不触发重绘。 */
    public synchronized String version() {
        if (imageAt == null) throw new Missing("图片尚未生成，请检查中文字体与数据目录权限");
        return Long.toString(imageAt.toEpochMilli());
    }

    /** 聚合页面与渲染状态，让“保存成功但图片失败”能明确显示，而不误报成未保存。 */
    public synchronized Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", settings); result.put("layout", layout);
        result.put("date", today()); result.put("pregnancy", pregnancy(settings, today()));
        result.put("weather", weather); result.put("weatherDescription", WeatherClient.description(weather.code()));
        boolean expired = weather.lastSuccessAt() == null || weather.lastSuccessAt().plusSeconds(settings.weather().refreshMinutes() * 60L).isBefore(Instant.now());
        result.put("weatherCached", weather.stale() || expired || !sameLocation(settings, weather));
        result.put("todos", todos(today())); result.put("movements", movements(today()));
        result.put("activeSession", sessions.stream().filter(s -> isActiveToday(s, today(), ZoneId.of(settings.timezone()))).findFirst().orElse(null));
        result.put("imageAt", imageAt); result.put("renderError", renderError);
        result.put("font", renderer.fontWarning.isEmpty() ? "中文字体可用" : renderer.fontWarning);
        return result;
    }

    public synchronized void saveSettings(Settings value) throws IOException {
        validateSettings(value);
        store.save("settings.json", value); settings = value;
        weatherAttempt = null;
        render();
    }

    public synchronized void saveLayout(Layout value) throws IOException {
        validateLayout(value);
        Layout saved = new Layout(value.columns(), value.rows(), List.copyOf(value.widgets()));
        store.save("layout.json", saved); layout = saved; render();
    }

    public synchronized List<Todo> todos(LocalDate date) {
        return todos.stream().filter(t -> t.date().equals(date)).sorted(Comparator.comparingInt(Todo::sort)).toList();
    }

    public synchronized Todo saveTodo(String id, Todo input) throws IOException {
        validateTodo(input);
        if (id != null && todos.stream().noneMatch(t -> t.id().equals(id))) throw new Missing("安排不存在");
        Todo value = new Todo(id == null ? UUID.randomUUID().toString() : id, input.date(), input.content().trim(), input.done(), input.sort());
        List<Todo> updated = new ArrayList<>(todos);
        if (id != null) updated.removeIf(t -> t.id().equals(id));
        updated.add(value);
        store.save("todos.json", updated); todos = updated; render(); return value;
    }

    public synchronized void deleteTodo(String id) throws IOException {
        List<Todo> updated = new ArrayList<>(todos);
        if (!updated.removeIf(t -> t.id().equals(id))) throw new Missing("安排不存在");
        store.save("todos.json", updated); todos = updated; render();
    }

    /** 跨日会话可在发生点击的各天查到；汇总严格按事件所在时区日期计算。 */
    public synchronized Map<String, Object> movements(LocalDate date) {
        ZoneId zone = ZoneId.of(settings.timezone());
        List<Session> day = sessions.stream().filter(s -> s.startedAt().atZone(zone).toLocalDate().equals(date)
                || s.taps().stream().anyMatch(t -> t.at().atZone(zone).toLocalDate().equals(date)))
                .sorted(Comparator.comparing(Session::startedAt).reversed()).toList();
        return Map.of("date", date, "count", countTaps(sessions, date, zone), "sessions", day);
    }

    public synchronized Session start() throws IOException {
        ZoneId zone = ZoneId.of(settings.timezone());
        LocalDate date = today();
        Optional<Session> active = sessions.stream().filter(s -> isActiveToday(s, date, zone)).findFirst();
        if (active.isPresent()) return active.get();
        Session value = new Session(UUID.randomUUID().toString(), Instant.now(), null, "", List.of());
        List<Session> updated = new ArrayList<>(sessions);
        // 昨日忘记结束的会话在其最后记录日结束，不把历史次数带入今天。
        updated.replaceAll(s -> s.endedAt() == null ? new Session(s.id(), s.startedAt(),
                (s.taps().isEmpty() ? s.startedAt() : s.taps().get(s.taps().size() - 1).at())
                        .atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(), s.note(), s.taps()) : s);
        updated.add(value);
        persistSessions(updated); return value;
    }

    /** 只允许当前会话记数/撤销/结束；先持久化副本再替换内存，写失败不会出现假保存。 */
    public synchronized Session changeSession(String id, String action, String note) throws IOException {
        Session old = sessions.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> new Missing("会话不存在"));
        require(action.equals("note") || old.endedAt() == null, "会话已结束，请开始新的计数");
        require(!action.equals("tap") && !action.equals("undo") || isActiveToday(old, today(), ZoneId.of(settings.timezone())),
                "日期已切换，请刷新页面并开始新的计数");
        List<Tap> taps = new ArrayList<>(old.taps());
        Instant end = old.endedAt();
        String newNote = old.note();
        switch (action) {
            case "tap" -> taps.add(new Tap(UUID.randomUUID().toString(), Instant.now()));
            case "undo" -> { require(!taps.isEmpty(), "当前会话没有可撤销记录"); taps.remove(taps.size() - 1); }
            case "end" -> end = Instant.now();
            case "note" -> { require(note != null && note.length() <= 500, "备注最多 500 字"); newNote = note; }
            default -> throw new IllegalArgumentException("未知操作");
        }
        Session value = new Session(old.id(), old.startedAt(), end, newNote, List.copyOf(taps));
        List<Session> updated = new ArrayList<>(sessions); updated.set(updated.indexOf(old), value);
        persistSessions(updated); return value;
    }

    private void persistSessions(List<Session> updated) throws IOException {
        store.save("movements.json", new MovementFile(updated)); sessions = updated; render();
    }

    /** 网络异常只更新缓存状态，不填充温度，不改变上次成功的城市和时间。 */
    public synchronized Weather refreshWeather() throws IOException {
        weatherAttempt = Instant.now();
        Weather updated;
        try { updated = weatherClient.fetch(settings); }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            updated = new Weather(weather.cityName(), weather.latitude(), weather.longitude(), weather.lastSuccessAt(),
                    weather.observedAt(), weather.temperature(), weather.code(), weather.high(), weather.low(), weather.humidity(), true,
                    weather.lastSuccessAt() == null ? "天气暂不可用，请稍后重试" : "刷新失败，正在显示上次成功数据", weather.windSpeed());
            log.warn("天气刷新失败，已保留缓存（{}）", e.getClass().getSimpleName());
        }
        store.save("weather.json", updated); weather = updated; render(); return weather;
    }

    /** 同一把锁串行化保存、定时生成和下载；失败不发布临时图，也不推进版本。 */
    public synchronized void render() {
        Instant now = Instant.now();
        try {
            Path pending = store.directory.resolve("dashboard.pending.png");
            renderer.render(pending, settings, layout, todos, sessions, weather, now);
            // 时钟回拨或同一毫秒连续保存时也递增；先写入临时图元数据，再一次性发布图片与版本。
            long next = imageAt == null ? now.toEpochMilli() : Math.max(now.toEpochMilli(), imageAt.toEpochMilli() + 1);
            Files.setLastModifiedTime(pending, FileTime.fromMillis(next));
            if (Files.getLastModifiedTime(pending).toMillis() != next) throw new IOException("文件系统无法保留毫秒版本时间");
            // 不支持原子替换时直接失败，避免进程中断留下半张图片或丢失旧版本。
            Files.move(pending, imagePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            imageAt = Instant.ofEpochMilli(next); renderError = "";
            imageWidth = settings.canvasWidth(); imageHeight = settings.canvasHeight(); kindleCache = null;
        } catch (Exception e) {
            renderError = "数据已保存，图片生成失败，保留上次成功图片。请检查数据目录权限、磁盘空间和中文字体。";
            log.warn("图片生成失败（{}），保留旧图", e.getClass().getSimpleName());
        }
    }

    public synchronized byte[] image() throws IOException {
        version();
        return Files.readAllBytes(imagePath());
    }

    public record ImageSnapshot(String version, String layout, int width, int height, byte[] bytes) {}
    public synchronized ImageSnapshot imageSnapshot() throws IOException {
        return new ImageSnapshot(version(), KindleFrame.Info.of(imageWidth,imageHeight).layout(), imageWidth,imageHeight,image());
    }
    public synchronized Map<String,Object> versionInfo() {
        var info = KindleFrame.Info.of(imageWidth,imageHeight);
        try {
            return Map.of("version",version(),"kindleVersion",kindleContentFrame().version(),"layout",info.layout(),"rotation",info.rotation(),"width",imageWidth,"height",imageHeight);
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    public synchronized KindleFrame.Bundle kindleFrame() throws IOException {
        String version = version();
        if (kindleCache == null || !kindleCache.version().equals(version)) kindleCache = KindleFrame.create(version,image(),renderer);
        return kindleCache;
    }

    private KindleFrame.Bundle contentCache;
    private String contentSourceVersion;
    public synchronized KindleFrame.Bundle kindleContentFrame() throws IOException {
        String source=version();
        if (contentCache==null || !source.equals(contentSourceVersion)) {
            contentCache=KindleFrame.create(source,image(),renderer,true);
            contentSourceVersion=source;
        }
        return contentCache;
    }

    /** 固定在整点、半点生成，即使数据没有变化也发布新版本；午夜自然包含在内。 */
    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Shanghai")
    public synchronized void scheduledRender() { render(); }

    /** 天气仍按现有设置间隔检查，更新缓存成功后沿用即时重绘链路。 */
    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public synchronized void tick() {
        Instant now = Instant.now();
        Instant previous = weatherAttempt == null ? weather.lastSuccessAt() : weatherAttempt;
        boolean weatherDue = previous == null || previous.plusSeconds(settings.weather().refreshMinutes() * 60L).isBefore(now)
                || weatherAttempt == null && !sameLocation(settings, weather);
        if (weatherDue) {
            try { refreshWeather(); } catch (IOException e) { log.warn("天气缓存保存失败，请检查数据目录"); }
        }
    }

    static void validateSettings(Settings value) {
        require(value != null, "设置不能为空");
        text(value.title(), 80, "看板名称");
        require(value.timezone() != null && ZoneId.getAvailableZoneIds().contains(value.timezone()), "请选择有效 IANA 时区，如 Asia/Shanghai");
        require(value.canvasWidth() >= 400 && value.canvasWidth() <= 3000 && value.canvasHeight() >= 400 && value.canvasHeight() <= 3000, "图片宽高必须在 400–3000 像素之间");
        require(value.defaultRenderMinutes() >= 1 && value.defaultRenderMinutes() <= 1440, "重绘间隔必须在 1–1440 分钟之间");
        require(value.dueDate() == null || value.dueDate().getYear() >= 1900 && value.dueDate().getYear() <= 2200, "预产期年份必须在 1900–2200 之间");
        Location location = value.weather(); require(location != null, "天气位置不能为空"); text(location.cityName(), 60, "城市名称");
        require(Double.isFinite(location.latitude()) && Math.abs(location.latitude()) <= 90 && Double.isFinite(location.longitude()) && Math.abs(location.longitude()) <= 180, "经纬度范围不正确");
        require(location.refreshMinutes() >= 10 && location.refreshMinutes() <= 1440, "天气间隔必须在 10–1440 分钟之间");
    }

    static void validateLayout(Layout value) {
        require(value != null && value.columns() == 12 && value.rows() == 24, "布局必须是 12 列 × 24 行");
        require(value.widgets() != null && value.widgets().size() <= 60, "模块最多 60 个");
        Set<String> ids = new HashSet<>();
        for (Widget w : value.widgets()) {
            require(w != null && w.id() != null && w.id().matches("[a-zA-Z0-9_-]{1,80}") && ids.add(w.id()), "模块标识无效或重复");
            require(w.type() != null && TYPES.contains(w.type()), "未知模块类型");
            require(w.x() >= 0 && w.y() >= 0 && w.w() >= 1 && w.h() >= 1 && w.x() + (long) w.w() <= 12 && w.y() + (long) w.h() <= 24, "模块超出画布边界");
            require(w.title() != null && w.title().length() <= 80 && w.text() != null && w.text().length() <= 5000, "标题最多 80 字，正文最多 5000 字");
            require(w.fontSize() >= 12 && w.fontSize() <= 120 && w.padding() >= 0 && w.padding() <= 60, "字号必须在 12–120、边距在 0–60 之间");
            require(w.textAlign() != null && Set.of("left", "center", "right").contains(w.textAlign()), "对齐方式无效");
            require(w.targetDate() == null || w.targetDate().getYear() >= 1900 && w.targetDate().getYear() <= 2200, "目标日期年份必须在 1900–2200 之间");
            for (Widget other : value.widgets()) {
                if (other == null || other == w || !w.enabled() || !other.enabled()) continue;
                require(!(w.x() < other.x() + (long) other.w() && w.x() + (long) w.w() > other.x() && w.y() < other.y() + (long) other.h() && w.y() + (long) w.h() > other.y()), "可见模块不能重叠，请先移动或隐藏占位模块");
            }
        }
    }

    static void validateTodo(Todo value) {
        require(value != null && value.date() != null && value.date().getYear() >= 1900 && value.date().getYear() <= 2200, "请选择有效安排日期");
        text(value.content(), 500, "安排内容");
    }
    static void text(String value, int limit, String name) { require(value != null && !value.isBlank() && value.length() <= limit, name + "不能为空且最多 " + limit + " 字"); }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    /** 用于区分不存在资源与输入错误。 */
    static class Missing extends RuntimeException { Missing(String message) { super(message); } }
}
