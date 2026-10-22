package com.example.kindleboard;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static com.example.kindleboard.Models.*;

/** 单看板接口：本机/可信家庭网络使用，无登录，无任意文件访问入口。 */
@RestController
@RequestMapping("/api")
public class BoardController {
    private final BoardService board;
    public BoardController(BoardService board) { this.board = board; }

    @GetMapping("/health") public Map<String, Boolean> health() { return Map.of("ok", true); }
    @GetMapping("/state") public Map<String, Object> state() { return board.state(); }
    @GetMapping("/settings") public Settings settings() { return board.settings(); }
    @PutMapping("/settings") public Map<String, Object> settings(@RequestBody Settings settings) throws IOException { board.saveSettings(settings); return board.state(); }
    @GetMapping("/layout") public Layout layout() { return board.layout(); }
    @PutMapping("/layout") public Map<String, Object> layout(@RequestBody Layout layout) { throw new IllegalArgumentException("当前采用固定模板，请通过 /api/content 修改内容，不能调整模块尺寸或位置"); }
    @GetMapping("/content") public List<CardText> content() { return board.content(); }
    @PutMapping("/content") public Map<String, Object> content(@RequestBody List<CardText> values) throws IOException { board.saveContent(values); return board.state(); }
    @GetMapping("/todos") public List<Todo> todos(@RequestParam(required = false) LocalDate date) { return board.todos(date(date)); }
    @PostMapping("/todos") public Todo add(@RequestBody Todo todo) throws IOException { return board.saveTodo(null, todo); }
    @PutMapping("/todos/{id}") public Todo update(@PathVariable String id, @RequestBody Todo todo) throws IOException { return board.saveTodo(id, todo); }
    @DeleteMapping("/todos/{id}") public Map<String, Boolean> delete(@PathVariable String id) throws IOException { board.deleteTodo(id); return Map.of("ok", true); }
    @GetMapping("/movements") public Map<String, Object> movements(@RequestParam(required = false) LocalDate date) { return board.movements(date(date)); }
    @PostMapping("/movements/start") public Session start() throws IOException { return board.start(); }
    @PostMapping("/movements/{id}/tap") public Session tap(@PathVariable String id) throws IOException { return board.changeSession(id, "tap", null); }
    @PostMapping("/movements/{id}/undo") public Session undo(@PathVariable String id) throws IOException { return board.changeSession(id, "undo", null); }
    @PostMapping("/movements/{id}/end") public Session end(@PathVariable String id) throws IOException { return board.changeSession(id, "end", null); }
    @PutMapping("/movements/{id}/note") public Session note(@PathVariable String id, @RequestBody Note note) throws IOException { return board.changeSession(id, "note", note.note()); }
    @GetMapping("/weather") public Weather weather() { return board.weather(); }
    @PostMapping("/weather/refresh") public Weather refresh() throws IOException { return board.refreshWeather(); }
    @PostMapping("/render") public Map<String, Object> render() { board.render(); return board.state(); }

    /** Kindle 轮询只查询版本；禁止浏览器和中间代理缓存，不触发渲染。 */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").header(HttpHeaders.EXPIRES, "0")
                .body(Map.of("version", board.version()));
    }

    /** 只读取最新成功 PNG；可附加 ?v=版本 避免缓存，参数不选择历史图，也不触发生成。 */
    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@RequestParam(defaultValue = "false") boolean download) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=dashboard.png")
                .body(board.image());
    }

    private LocalDate date(LocalDate value) { return value == null ? LocalDate.now(ZoneId.of(board.settings().timezone())) : value; }
    public record Note(/* 会话备注 */ String note) {}

    @ExceptionHandler(BoardService.Missing.class)
    public ResponseEntity<?> missing(BoardService.Missing e) { return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> bad(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> malformed(Exception e) { return ResponseEntity.badRequest().body(Map.of("message", "请求格式有误，请检查日期、数字及必填字段")); }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> storage(IOException e) { return ResponseEntity.status(500).body(Map.of("message", "文件读写失败，请检查数据目录权限及磁盘空间")); }
}
