package com.example.kindleboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static com.example.kindleboard.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 只锁住核心闭环与失败保护，不为每个 getter 添加测试。 */
class BoardTest {
    @TempDir Path directory;
    ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    WeatherClient client;
    PngRenderer renderer;
    BoardService board;

    @BeforeEach void setup() throws Exception {
        client = mock(WeatherClient.class);
        renderer = spy(new PngRenderer(""));
        board = new BoardService(new JsonStore(mapper, directory.toString()), renderer, client);
    }

    @Test void kindleFingerprintExcludesClockAcrossTenMinutes() throws Exception {
        Path first=directory.resolve("first.png"), second=directory.resolve("second.png");
        Instant start=Instant.parse("2026-11-01T04:00:00Z");
        renderer.render(first,board.settings(),board.layout(),List.of(),List.of(),board.weather(),start);
        renderer.render(second,board.settings(),board.layout(),List.of(),List.of(),board.weather(),start.plusSeconds(600));
        assertFalse(Arrays.equals(Files.readAllBytes(first),Files.readAllBytes(second)));
        assertEquals(KindleFrame.create("1",Files.readAllBytes(first),renderer,true).version(),
                KindleFrame.create("2",Files.readAllBytes(second),renderer,true).version());
    }

    @Test void kindleContentVersionIgnoresRenderTimeButTracksVisibleContent() throws Exception {
        String before=board.kindleContentFrame().version();
        board.scheduledRender();
        assertEquals(before,board.kindleContentFrame().version());
        assertEquals(before,reopen().kindleContentFrame().version());
        board.saveTodo(null,new Todo(null,LocalDate.now(ZoneId.of(board.settings().timezone())),"new visible task",false,10));
        assertNotEquals(before,board.kindleContentFrame().version());
        assertEquals(board.kindleContentFrame().version(),board.versionInfo().get("kindleVersion"));
    }

    @Test void defaultFilesAndRealGrayPng() throws Exception {
        for (String name : List.of("settings.json", "layout.json", "todos.json", "movements.json", "weather.json", "dashboard.png")) assertTrue(Files.exists(directory.resolve(name)), name);
        BufferedImage image = ImageIO.read(directory.resolve("dashboard.png").toFile());
        assertEquals(1072, image.getWidth()); assertEquals(1448, image.getHeight());
        assertEquals(1, image.getRaster().getNumBands()); assertTrue(renderer.fontWarning.isEmpty());
        boolean titleInk = false;
        for (int y=30; y<70; y++) for (int x=30; x<300; x++) if (image.getRaster().getSample(x,y,0)<160) titleInk = true;
        assertTrue(titleInk, "单行卡片标题必须实际绘制，不能因行高计算丢失");
        assertEquals("待设置", PngRenderer.pregnancy(board.settings(), LocalDate.now()).gestation());
    }

    @Test void todoCrudPersistsAndChangesPng() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(board.settings().timezone()));
        byte[] before = board.image();
        String version = board.version();
        Todo created = board.saveTodo(null, new Todo(null, today, "阅读半小时", false, 10));
        assertNotEquals(version, board.version());
        assertFalse(Arrays.equals(before, board.image()));
        board.saveTodo(created.id(), new Todo(null, today, "阅读一小时", true, 10));
        assertTrue(board.todos(today).get(0).done());
        BoardService restarted = reopen();
        assertEquals("阅读一小时", restarted.todos(today).get(0).content());
        restarted.deleteTodo(created.id()); assertTrue(restarted.todos(today).isEmpty());
        assertThrows(BoardService.Missing.class, () -> restarted.deleteTodo(created.id()));
    }

    @Test void fiveTapsUndoOneRestartAndEnd() throws Exception {
        String version = board.version();
        Session session = board.start(); assertEquals(session.id(), board.start().id());
        assertNotEquals(version, board.version());
        version = board.version();
        for (int i = 0; i < 5; i++) board.changeSession(session.id(), "tap", null);
        assertNotEquals(version, board.version());
        board.changeSession(session.id(), "undo", null);
        LocalDate date = LocalDate.now(ZoneId.of(board.settings().timezone()));
        assertEquals(4L, board.movements(date).get("count"));
        BoardService restarted = reopen();
        assertEquals(4L, restarted.movements(date).get("count"));
        assertEquals(4, ((Session) restarted.state().get("activeSession")).taps().size());
        restarted.changeSession(session.id(), "note", "晚间记录");
        restarted.changeSession(session.id(), "end", null);
        assertThrows(IllegalArgumentException.class, () -> restarted.changeSession(session.id(), "tap", null));
        assertNotEquals(session.id(), restarted.start().id());
    }

    @Test void yesterdayUnfinishedSessionDoesNotCarryIntoToday() throws Exception {
        ZoneId zone = ZoneId.of(board.settings().timezone());
        LocalDate today = LocalDate.now(zone);
        Instant yesterday = today.minusDays(1).atTime(23, 50).atZone(zone).toInstant();
        Session old = new Session("yesterday", yesterday, null, "保留备注", List.of(new Tap("tap", yesterday)));
        new JsonStore(mapper, directory.toString()).save("movements.json", new BoardService.MovementFile(List.of(old)));
        board = reopen();
        assertNull(board.state().get("activeSession"));
        assertEquals(0L, board.movements(today).get("count"));
        assertEquals(List.of(), board.movements(today).get("sessions"));
        assertFalse(PngRenderer.isActiveToday(old, today, zone));
        assertTrue(PngRenderer.isActiveToday(old, today.minusDays(1), zone));
        assertThrows(IllegalArgumentException.class, () -> board.changeSession(old.id(), "tap", null));
        Session fresh = board.start();
        assertNotEquals(old.id(), fresh.id());
        assertTrue(fresh.taps().isEmpty());
        board.changeSession(fresh.id(), "tap", null);
        assertEquals(1L, board.movements(today).get("count"));
        BoardService restarted = reopen();
        assertEquals(fresh.id(), ((Session) restarted.state().get("activeSession")).id());
        Session history = ((List<Session>) restarted.movements(today.minusDays(1)).get("sessions")).get(0);
        assertEquals(1, history.taps().size());
        assertEquals("保留备注", history.note());
        assertEquals(today.atStartOfDay(zone).toInstant(), history.endedAt());
    }

    @Test void crossMidnightCountsByTapDateInConfiguredZone() {
        Instant a = Instant.parse("2026-10-20T15:59:00Z"), b = Instant.parse("2026-10-20T16:01:00Z");
        List<Session> sessions = List.of(new Session("s", a, null, "", List.of(new Tap("1", a), new Tap("2", b))));
        assertEquals(1, PngRenderer.countTaps(sessions, LocalDate.of(2026,10,20), ZoneId.of("Asia/Shanghai")));
        assertEquals(1, PngRenderer.countTaps(sessions, LocalDate.of(2026,10,21), ZoneId.of("Asia/Shanghai")));
    }

    @Test void pregnancyAndPastDueDate() throws Exception {
        LocalDate today = LocalDate.of(2026,10,21);
        Settings defaults = board.settings();
        Settings settings = new Settings(defaults.title(), defaults.timezone(), 800, 1200, defaults.weather(), today.plusDays(100), 60);
        assertEquals("25周5天", PngRenderer.pregnancy(settings, today).gestation());
        assertEquals("距预产期还有 100 天", PngRenderer.pregnancy(settings, today).countdown());
        assertEquals("距预产期已过 1 天", PngRenderer.pregnancy(settings, today.plusDays(101)).countdown());
        board.saveSettings(settings);
        assertEquals(800, ImageIO.read(directory.resolve("dashboard.png").toFile()).getWidth());
        assertEquals(settings, reopen().settings());
    }

    @Test void weatherFailureKeepsSuccessAndTimestamp() throws Exception {
        when(client.fetch(any())).thenThrow(new IOException("offline"));
        assertNull(board.refreshWeather().temperature()); assertTrue(board.weather().stale());
        Weather success = new Weather("郑州",34.7466,113.6254,Instant.now(),"2026-10-21T12:00",18.5,2,22.0,13.0,60.0,false,"Open-Meteo", 4.5);
        doReturn(success).when(client).fetch(any()); board.refreshWeather();
        doThrow(new IOException("offline")).when(client).fetch(any());
        board.refreshWeather();
        assertTrue(board.weather().stale()); assertEquals(18.5, board.weather().temperature());
        assertEquals(success.lastSuccessAt(), board.weather().lastSuccessAt());
        assertEquals(success.lastSuccessAt(), reopen().weather().lastSuccessAt());
    }

    @Test void fixedTemplateIgnoresOldGeometryAndClipsLongText() throws Exception {
        List<Widget> widgets = new ArrayList<>(board.layout().widgets());
        Widget date = widgets.get(0), weather = widgets.get(1);
        widgets.set(0, position(date, 6,0)); widgets.set(1, position(weather,0,0));
        Settings s = board.settings();
        Settings full = new Settings(s.title(),s.timezone(),1086,1448,s.weather(),null,60);
        Instant now = Instant.parse("2026-10-21T01:40:00Z");
        Path before = directory.resolve("before.png"), after = directory.resolve("after.png");
        renderer.render(before,full,board.layout(),List.of(),List.of(),board.weather(),now);
        renderer.render(after,full,new Layout(12,24,widgets),List.of(),List.of(),board.weather(),now);
        assertArrayEquals(Files.readAllBytes(before),Files.readAllBytes(after),"旧坐标变化不得改变固定模板");
        assertThrows(IllegalArgumentException.class, () -> board.saveTodo(null,new Todo(null,LocalDate.now()," ",false,0)));
        assertThrows(IllegalArgumentException.class, () -> board.saveSettings(new Settings(s.title(),"invalid",400,400,s.weather(),null,60)));
        List<CardText> content = new ArrayList<>(FixedContent.read(board.layout()));
        CardText hidden = content.get(1);
        content.set(1,new CardText(hidden.id(),hidden.title(),"这是已移除的提醒".repeat(400),null));
        renderer.render(after,full,FixedContent.merge(board.layout(),content),List.of(),List.of(),board.weather(),now);
        assertArrayEquals(Files.readAllBytes(before),Files.readAllBytes(after),"隐藏提醒的内容不得影响新图");
        CardText appointment = content.get(0);
        content.set(0,new CardText(appointment.id(),"超长产检信息".repeat(10),appointment.text(),null));
        renderer.render(after,full,FixedContent.merge(board.layout(),content),List.of(),List.of(),board.weather(),now);
        BufferedImage image = ImageIO.read(before.toFile()), changed = ImageIO.read(after.toFile());
        assertFalse(Arrays.equals(Files.readAllBytes(before),Files.readAllBytes(after)));
        // 按1080×1456逻辑坐标等比输出到1086×1448，容许一个像素的抗锯齿边缘。
        for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
            if(x>=155 && x<503 && y>=1040 && y<1078) continue;
            assertEquals(image.getRGB(x,y),changed.getRGB(x,y),"长文字只能影响产检标题槽位");
        }
    }

    @Test void fixedContentPreservesOldDataAndPersists() throws Exception {
        List<CardText> contents = new ArrayList<>(board.content());
        contents.set(0,new CardText(contents.get(0).id(),"下次产检：11月2日09:30","演示医院",null));
        contents.set(3,new CardText(contents.get(3).id(),"周末散步","",LocalDate.of(2026,10,24)));
        List<Widget> previous = board.layout().widgets();
        board.saveContent(contents);
        assertTrue(board.layout().widgets().containsAll(previous));
        assertEquals(contents,reopen().content());
        assertEquals("农历正月初一",PngRenderer.lunarDate(LocalDate.of(2026,2,17)));
        assertThrows(IllegalArgumentException.class,()->board.saveContent(List.of(contents.get(0))));
    }

    @Test void renderFailureAndStorageFailureKeepOldFiles() throws Exception {
        byte[] previous = board.image(); Object imageAt = board.state().get("imageAt");
        String version = board.version();
        doThrow(new IOException("render failed")).when(renderer).render(any(),any(),any(),any(),any(),any(),any());
        board.render(); assertArrayEquals(previous,board.image()); assertEquals(imageAt,board.state().get("imageAt"));
        assertEquals(version, board.version());
        assertEquals(version, reopen().version());
        assertFalse(((String)board.state().get("renderError")).isEmpty());
        // 用目录占住临时文件名，模拟磁盘写入失败，正式 JSON 和内存都必须保留。
        Files.createDirectory(directory.resolve("todos.json.tmp"));
        assertThrows(IOException.class, () -> board.saveTodo(null,new Todo(null,LocalDate.now(),"不能假保存",false,0)));
        assertTrue(board.todos(LocalDate.now()).isEmpty());
        assertEquals(0,mapper.readTree(directory.resolve("todos.json").toFile()).size());
    }

    @Test void restartReusesValidImageAndRegeneratesMissingOrBrokenImage() throws Exception {
        byte[] previous = board.image();
        String version = board.version();
        clearInvocations(renderer);
        assertEquals(version, reopen().version());
        assertArrayEquals(previous, board.image());
        verify(renderer, never()).render(any(),any(),any(),any(),any(),any(),any());
        Files.delete(board.imagePath());
        BoardService recovered = reopen();
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(recovered.image())));
        Files.writeString(board.imagePath(), "broken PNG");
        recovered = reopen();
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(recovered.image())));
        assertEquals(recovered.version(), reopen().version());
        verify(renderer, times(2)).render(any(),any(),any(),any(),any(),any(),any());
    }

    @Test void halfHourlyScheduleAlwaysAdvancesVersionEvenWithClockRollback() throws Exception {
        Scheduled schedule = BoardService.class.getMethod("scheduledRender").getAnnotation(Scheduled.class);
        var cron = CronExpression.parse(schedule.cron());
        var hour = LocalDateTime.of(2026, 10, 22, 9, 0);
        assertEquals(hour.plusMinutes(30), cron.next(hour));
        assertEquals(hour.plusHours(1), cron.next(hour.plusMinutes(30)));
        assertEquals("Asia/Shanghai", schedule.zone());
        // 用未来的成功时间模拟系统时钟回拨，同时锁住同毫秒连续生成的递增规则。
        long future = System.currentTimeMillis() + 3600000;
        Files.setLastModifiedTime(board.imagePath(), java.nio.file.attribute.FileTime.fromMillis(future));
        BoardService restarted = reopen();
        restarted.scheduledRender();
        assertEquals(Long.toString(future + 1), restarted.version());
        restarted.scheduledRender();
        assertEquals(Long.toString(future + 2), restarted.version());
        assertEquals(restarted.version(), reopen().version());
    }

    @Test void failedPublicationKeepsImageAndVersion() throws Exception {
        byte[] previous = board.image();
        String version = board.version();
        // 阻塞发布前的临时输出，验证存储故障不会提前覆盖正式图片或版本。
        Files.createDirectory(directory.resolve("dashboard.pending.png"));
        Files.writeString(directory.resolve("dashboard.pending.png/block"), "block");
        board.render();
        assertEquals(version, board.version());
        assertArrayEquals(previous, board.image());
        assertEquals(version, reopen().version());
        assertFalse(((String) board.state().get("renderError")).isEmpty());
    }

    @Test void concurrentScheduledAndManualUpdatesPublishOnlyCompleteImages() throws Exception {
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        doAnswer(call -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            try { return call.callRealMethod(); }
            finally { active.decrementAndGet(); }
        }).when(renderer).render(any(),any(),any(),any(),any(),any(),any());
        long previous = Long.parseLong(board.version());
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Void>> work = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                work.add(() -> { board.scheduledRender(); return null; });
                work.add(() -> { board.saveTodo(null, new Todo(null, LocalDate.now(), "并发保存", false, 0)); return null; });
                work.add(() -> {
                    assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(board.image())));
                    assertNotNull(board.version());
                    return null;
                });
            }
            for (Future<Void> task : workers.invokeAll(work, 30, TimeUnit.SECONDS)) task.get();
        } finally { workers.shutdownNow(); }
        assertEquals(1, maximum.get());
        assertTrue(Long.parseLong(board.version()) >= previous + 6);
        assertEquals(3, board.todos(LocalDate.now()).size());
        assertEquals(board.version(), reopen().version());
        assertArrayEquals(board.image(), reopen().image());
        assertEquals("", board.state().get("renderError"));
    }

    @Test void versionAndCacheBustingImageReadsNeverRender() throws Exception {
        var http = MockMvcBuilders.standaloneSetup(new BoardController(board)).build();
        String version = board.version();
        byte[] previous = board.image();
        clearInvocations(renderer);
        for (int i = 0; i < 2; i++) {
            http.perform(get("/api/version")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(version))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Pragma", "no-cache"))
                    .andExpect(header().string("Expires", "0"));
            http.perform(get("/api/image").param("v", i == 0 ? version : "old-version"))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().bytes(previous));
        }
        verify(renderer, never()).render(any(),any(),any(),any(),any(),any(),any());
    }

    @Test void noSuccessfulImageReturnsUncached404WithoutRendering() throws Exception {
        Files.delete(board.imagePath());
        doThrow(new IOException("render failed")).when(renderer).render(any(),any(),any(),any(),any(),any(),any());
        BoardService unavailable = reopen();
        clearInvocations(renderer);
        var http = MockMvcBuilders.standaloneSetup(new BoardController(unavailable)).build();
        http.perform(get("/api/version")).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        http.perform(get("/api/image")).andExpect(status().isNotFound());
        verify(renderer, never()).render(any(),any(),any(),any(),any(),any(),any());
    }

    @Test void brokenJsonIsNotResetAndGetsBackup() throws Exception {
        Files.writeString(directory.resolve("todos.json"),"{broken");
        assertThrows(IOException.class,this::reopen);
        assertEquals("{broken",Files.readString(directory.resolve("todos.json")));
        try (var files = Files.list(directory)) { assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("todos.json.corrupt-"))); }
    }

    @Test void httpContractsAndInvalidInputs() throws Exception {
        var http = MockMvcBuilders.standaloneSetup(new BoardController(board)).build();
        http.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        http.perform(get("/api/state")).andExpect(status().isOk()).andExpect(jsonPath("$.pregnancy.gestation").value("待设置"));
        http.perform(get("/api/content")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        http.perform(put("/api/layout").contentType("application/json").content(mapper.writeValueAsBytes(board.layout())))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/image")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        http.perform(get("/api/image?download=true")).andExpect(header().string("Content-Disposition","attachment; filename=dashboard.png"));
        http.perform(get("/api/todos?date=not-a-date")).andExpect(status().isBadRequest());
        http.perform(post("/api/todos").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        http.perform(put("/api/settings").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        http.perform(delete("/api/todos/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test void generateReferenceStyleExampleWithExplicitDemoData() throws Exception {
        LocalDate day = LocalDate.of(2026,10,21);
        Instant now = Instant.parse("2026-10-21T01:40:00Z");
        Settings settings = new Settings("家庭信息看板","Asia/Shanghai",1086,1448,board.settings().weather(),day.plusDays(110),60);
        String[] texts={"产检资料整理","散步20分钟","补充营养与温水","晚上22:30前休息"};
        List<Todo> todos=new ArrayList<>();
        for (int index=0;index<texts.length;index++) todos.add(new Todo("demo-"+index,day,texts[index],false,index));
        List<Session> sessions=new ArrayList<>();
        for (int index=0;index<2;index++) {
            Instant start=Instant.parse(index==0 ? "2026-10-21T01:05:00Z" : "2026-10-21T01:20:00Z");
            List<Tap> taps=new ArrayList<>();
            for (int tap=0;tap<(index==0 ? 12 : 10);tap++) taps.add(new Tap("demo-tap-"+index+"-"+tap,start.plusSeconds(tap*45L)));
            sessions.add(new Session("demo-session-"+index,start,start.plusSeconds(600),"演示记录",taps));
        }
        List<CardText> content=List.of(new CardText("fixed-appointment","下次产检：11月2日09:30","郑州市妇幼保健院",null),
                new CardText("fixed-reminder-1","保持心情放松","好情绪，是给宝宝最好的礼物。",null),
                new CardText("fixed-reminder-2","避免久坐，适当活动","活动一下，身体更轻松。",null),
                new CardText("fixed-reminder-3","记得记录今天的小变化","哪怕很小，也是珍贵的成长。",null),
                new CardText("fixed-footer","底部寄语","把日子过得安稳一点，等你慢慢来到我们身边。",null));
        Weather weather=new Weather("郑州",34.7466,113.6254,now,"2026-10-21T09:30",23.0,0,26.0,17.0,52.0,false,"仅供演示", 3.8);
        Path target=Path.of("target/fixed-template-preview.png");
        renderer.render(target,settings,FixedContent.merge(board.layout(),content),todos,sessions,weather,now);
        BufferedImage example=ImageIO.read(target.toFile());
        assertEquals(1086,example.getWidth());
        assertEquals(1448,example.getHeight());
        Settings landscape = new Settings(settings.title(),settings.timezone(),1448,1086,settings.weather(),settings.dueDate(),60);
        Path wideTarget=Path.of("target/fixed-template-landscape-preview.png");
        renderer.render(wideTarget,landscape,FixedContent.merge(board.layout(),content),todos,sessions,weather,now);
        BufferedImage wide=ImageIO.read(wideTarget.toFile());
        assertEquals(1448,wide.getWidth()); assertEquals(1086,wide.getHeight());
        assertEquals(1,wide.getRaster().getNumBands());
        board.saveSettings(landscape);
        BoardService restarted=reopen();
        assertEquals(landscape,restarted.settings(),"重启必须保持横版宽高");
        BufferedImage saved=ImageIO.read(restarted.imagePath().toFile());
        assertEquals(1448,saved.getWidth()); assertEquals(1086,saved.getHeight());
    }

    @Test void kindleBundleBindsBothLayoutsAndPaperToOneImageVersion() throws Exception {
        var http = MockMvcBuilders.standaloneSetup(new BoardController(board)).build();
        for (int[] size : List.of(new int[]{1072,1448},new int[]{1448,1072},new int[]{1600,900})) {
            var old=board.settings();
            board.saveSettings(new Settings(old.title(),old.timezone(),size[0],size[1],old.weather(),old.dueDate(),old.defaultRenderMinutes()));
            clearInvocations(renderer);
            var response=http.perform(get("/api/kindle/frame")).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            Map<String,byte[]> entries=unzip(response.getContentAsByteArray());
            assertEquals(Set.of("manifest.txt","frame.png","clock-background.png","battery-background.png"),entries.keySet());
            Properties manifest=new Properties(); manifest.load(new java.io.ByteArrayInputStream(entries.get("manifest.txt")));
            String layout=size[0]>size[1]?"landscape-v1":"portrait-v1";
            assertEquals(layout,manifest.getProperty("layout"));
            assertEquals(board.version(),manifest.getProperty("version"));
            assertEquals(manifest.getProperty("version"),response.getHeader("X-Board-Version"));
            assertEquals(layout,response.getHeader("X-Board-Layout"));
            assertEquals(size[0]>size[1]?"90":"0",manifest.getProperty("rotation"));
            BufferedImage image=ImageIO.read(new java.io.ByteArrayInputStream(entries.get("frame.png")));
            assertEquals(1072,image.getWidth()); assertEquals(1448,image.getHeight());
            for(String field:List.of("clock","clock_bg","battery","home")) {
                int x=Integer.parseInt(manifest.getProperty(field+"_x")),y=Integer.parseInt(manifest.getProperty(field+"_y"));
                int w=Integer.parseInt(manifest.getProperty(field+"_w")),h=Integer.parseInt(manifest.getProperty(field+"_h"));
                assertTrue(x>=0&&y>=0&&w>0&&h>0&&x+w<=1072&&y+h<=1448,field);
            }
            BufferedImage paper=ImageIO.read(new java.io.ByteArrayInputStream(entries.get("clock-background.png")));
            assertEquals(Integer.parseInt(manifest.getProperty("clock_bg_w")),paper.getWidth());
            assertEquals(Integer.parseInt(manifest.getProperty("clock_bg_h")),paper.getHeight());
            assertSame(board.kindleFrame(),board.kindleFrame(),"Same successful version must reuse its immutable bundle");
            verify(renderer,never()).render(any(),any(),any(),any(),any(),any(),any());
        }
    }

    @Test void failedLayoutChangeKeepsPreviousKindleGenerationAcrossRestart() throws Exception {
        var before=board.kindleFrame(); var old=board.settings();
        doThrow(new IOException("layout render failed")).when(renderer).render(any(),any(),any(),any(),any(),any(),any());
        board.saveSettings(new Settings(old.title(),old.timezone(),1448,1072,old.weather(),old.dueDate(),60));
        assertEquals("portrait-v1",board.versionInfo().get("layout"));
        assertArrayEquals(before.bytes(),board.kindleFrame().bytes());
        var recovered=reopen();
        assertEquals("portrait-v1",recovered.versionInfo().get("layout"));
        assertEquals(before.version(),recovered.kindleFrame().version());
        Properties manifest=new Properties(); manifest.load(new java.io.ByteArrayInputStream(unzip(recovered.kindleFrame().bytes()).get("manifest.txt")));
        assertEquals("portrait-v1",manifest.getProperty("layout"));
    }

    private static Map<String,byte[]> unzip(byte[] bytes) throws IOException {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) { assertNull(entries.put(entry.getName(),zip.readAllBytes())); zip.closeEntry(); }
        }
        return entries;
    }

    private BoardService reopen() throws IOException { return new BoardService(new JsonStore(mapper,directory.toString()),renderer,client); }
    private Widget position(Widget w,int x,int y) { return new Widget(w.id(),w.type(),w.enabled(),x,y,w.w(),w.h(),w.title(),w.text(),w.targetDate(),w.fontSize(),w.textAlign(),w.padding()); }
}
