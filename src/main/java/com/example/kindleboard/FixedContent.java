package com.example.kindleboard;

import java.util.*;
import static com.example.kindleboard.Models.*;

/** 新模板只读取固定的五处文案；兼容原有便签和倒计时内容，不清空旧布局文件。 */
final class FixedContent {
    private FixedContent() {}
    static final List<String> IDS = List.of("fixed-appointment", "fixed-reminder-1", "fixed-reminder-2", "fixed-reminder-3", "fixed-footer");

    static List<CardText> read(Layout layout) {
        Widget previousText = layout.widgets().stream().filter(w -> w.type().equals("TEXT") && !IDS.contains(w.id())).findFirst().orElse(null);
        Widget previousCountdown = layout.widgets().stream().filter(w -> w.type().equals("COUNTDOWN") && !IDS.contains(w.id())).findFirst().orElse(null);
        List<CardText> defaults = List.of(
                new CardText(IDS.get(0), "下次产检：待设置", "地点待设置", null),
                previousText == null ? new CardText(IDS.get(1), "保持心情放松", "好情绪，是给宝宝最好的礼物。", null)
                        : new CardText(IDS.get(1), previousText.title(), previousText.text(), previousText.targetDate()),
                new CardText(IDS.get(2), "避免久坐，适当活动", "活动一下，身体更轻松。", null),
                previousCountdown == null ? new CardText(IDS.get(3), "记得记录今天的小变化", "哪怕很小，也是珍贵的成长。", null)
                        : new CardText(IDS.get(3), previousCountdown.title(), previousCountdown.text(), previousCountdown.targetDate()),
                new CardText(IDS.get(4), "底部寄语", "把日子过得安稳一点，等你慢慢来到我们身边。", null));
        return defaults.stream().map(fallback -> layout.widgets().stream().filter(w -> w.id().equals(fallback.id())).findFirst()
                .map(w -> new CardText(w.id(), w.title(), w.text(), w.targetDate())).orElse(fallback)).toList();
    }

    /** 只替换五处文案，旧模块完整留存；这些记录不参与历史网格占位。 */
    static Layout merge(Layout existing, List<CardText> values) {
        BoardService.require(values != null && values.size() == IDS.size(), "请提交全部五处模板文案");
        Set<String> ids = new HashSet<>();
        List<Widget> widgets = new ArrayList<>(existing.widgets().stream().filter(w -> !IDS.contains(w.id())).toList());
        for (CardText value : values) {
            BoardService.require(value != null && value.id() != null && IDS.contains(value.id()) && ids.add(value.id()), "模板内容标识无效或重复");
            BoardService.require(value.title() != null && value.title().length() <= 80 && value.text() != null && value.text().length() <= 5000, "标题最多80字，正文最多5000字");
            BoardService.require(value.targetDate() == null || value.targetDate().getYear() >= 1900 && value.targetDate().getYear() <= 2200, "目标日期年份必须在1900–2200之间");
            widgets.add(new Widget(value.id(), "TEXT", false, 0, 0, 1, 1, value.title(), value.text(), value.targetDate(), 24, "left", 0));
        }
        return new Layout(existing.columns(), existing.rows(), List.copyOf(widgets));
    }
}
