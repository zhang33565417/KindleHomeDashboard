package com.example.kindleboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Supplier;

/** 单进程文件存储：完整写临时文件后替换，损坏文件保留并阻止静默清空。 */
@Component
public class JsonStore {
    final Path directory;
    private final ObjectMapper mapper;

    public JsonStore(ObjectMapper mapper, @Value("${board.data-dir:./data}") String directory) throws IOException {
        this.mapper = mapper;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }

    /** 仅由服务内部传入固定文件名；缺失才初始化，解析失败绝不替换原文件。 */
    public synchronized <T> T load(String name, Class<T> type, Supplier<T> defaults) throws IOException {
        Path path = directory.resolve(name);
        if (!Files.exists(path)) {
            T value = defaults.get();
            save(name, value);
            return value;
        }
        try {
            T value = mapper.readValue(path.toFile(), type);
            if (value == null) throw new IOException("JSON 不能是 null");
            return value;
        } catch (IOException e) {
            Files.copy(path, directory.resolve(name + ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.COPY_ATTRIBUTES);
            throw new IOException(name + " 已损坏，已保留原文件及备份。请修复后重新启动。", e);
        }
    }

    /** 临时文件和目标同目录；不支持原子移动的文件系统仅回退替换操作。 */
    public synchronized void save(String name, Object value) throws IOException {
        Path temporary = directory.resolve(name + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        replace(temporary, directory.resolve(name));
    }

    static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
