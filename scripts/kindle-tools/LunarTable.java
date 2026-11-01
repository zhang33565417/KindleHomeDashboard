import com.nlf.calendar.Solar;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

/** Offline lunar lookup: reuse the exact calendar library used by the server. */
public class LunarTable {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        var labels = new TreeMap<String,String>();
        try (var out = Files.newBufferedWriter(directory.resolve("calendar.tsv"), StandardCharsets.UTF_8)) {
            for (LocalDate day = LocalDate.of(2000,1,1); day.isBefore(LocalDate.of(2101,1,1)); day = day.plusDays(1)) {
                var lunar = Solar.fromYmd(day.getYear(), day.getMonthValue(), day.getDayOfMonth()).getLunar();
                String key = "lunar-" + lunar.getMonth() + "-" + lunar.getDay();
                labels.put(key, "农历" + lunar.getMonthInChinese() + "月" + lunar.getDayInChinese());
                out.write(day.format(DateTimeFormatter.BASIC_ISO_DATE) + "\t" + key + "\n");
            }
        }
        try (var out = Files.newBufferedWriter(directory.resolve("lunar-labels.tsv"), StandardCharsets.UTF_8)) {
            for (var item : labels.entrySet()) out.write(item.getKey() + "\t" + item.getValue() + "\n");
        }
    }
}
