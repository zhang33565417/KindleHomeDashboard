package com.example.kindleboard;

import java.time.*;
import java.util.List;

/** 文件模型同时作为接口数据，避免为个人工具再复制一套 DTO。 */
public final class Models {
    private Models() {}

    /** 看板全局设置；分辨率是实际输出像素，时区控制所有日期归属。 */
    public record Settings(
            /* 看板名称 */ String title,
            /* IANA 时区 */ String timezone,
            /* 图片宽度像素 */ int canvasWidth,
            /* 图片高度像素 */ int canvasHeight,
            /* 天气查询位置和间隔 */ Location weather,
            /* 用户预产期；未填写时为空 */ LocalDate dueDate,
            /* 定期重绘间隔（分钟） */ int defaultRenderMinutes) {}

    /** 手填天气位置，不把默认城市视为用户真实住址。 */
    public record Location(
            /* 展示城市 */ String cityName,
            /* 纬度 */ double latitude,
            /* 经度 */ double longitude,
            /* 天气刷新间隔（分钟） */ int refreshMinutes) {}

    /** 固定 12 列 24 行，所有坐标均为逻辑网格单位。 */
    public record Layout(
            /* 网格列数 */ int columns,
            /* 网格行数 */ int rows,
            /* 模块集合 */ List<Widget> widgets) {}

    /** 模块内容和样式共用同一配置，网页预览直接显示渲染后的 PNG。 */
    public record Widget(
            /* 模块唯一标识 */ String id,
            /* 内置模块类型 */ String type,
            /* 是否显示 */ boolean enabled,
            /* 左侧列坐标 */ int x,
            /* 顶部行坐标 */ int y,
            /* 占用列数 */ int w,
            /* 占用行数 */ int h,
            /* 模块标题 */ String title,
            /* 自定义文字正文 */ String text,
            /* 自定义倒计时日期 */ LocalDate targetDate,
            /* 正文字号（输出像素） */ int fontSize,
            /* left / center / right */ String textAlign,
            /* 卡片内边距像素 */ int padding) {}

    /** 按自然日保存的安排。 */
    public record Todo(
            /* 服务端生成的标识 */ String id,
            /* 安排所属日期 */ LocalDate date,
            /* 安排正文 */ String content,
            /* 是否完成 */ boolean done,
            /* 同日排序号 */ int sort) {}

    /** 一段手动计数，不进行医学评价。 */
    public record Session(
            /* 会话标识 */ String id,
            /* 开始时间 */ Instant startedAt,
            /* 结束时间；空表示计数中 */ Instant endedAt,
            /* 用户备注 */ String note,
            /* 每次点击的独立时间 */ List<Tap> taps) {}

    /** 按事件时间计算当天次数，跨午夜不重复记数。 */
    public record Tap(
            /* 点击标识 */ String id,
            /* 点击发生时刻 */ Instant at) {}

    /** 固定模板的文字槽位，只编辑内容，不携带位置、大小或字号。 */
    public record CardText(
            /* 固定内容槽位标识 */ String id,
            /* 主标题或产检信息第一行 */ String title,
            /* 补充文字 */ String text,
            /* 可选纪念日；填写后正文展示自然日倒计时 */ LocalDate targetDate) {}

    /** 天气成功数据及最近尝试状态；失败时保留成功数据和原位置。 */
    public record Weather(
            /* 成功数据所属城市 */ String cityName,
            /* 成功数据所属纬度 */ Double latitude,
            /* 成功数据所属经度 */ Double longitude,
            /* 请求成功时间 */ Instant lastSuccessAt,
            /* 上游数据时间（所在时区当地时间） */ String observedAt,
            /* 摄氏气温 */ Double temperature,
            /* WMO 天气代码 */ Integer code,
            /* 当天最高温 */ Double high,
            /* 当天最低温 */ Double low,
            /* 相对湿度百分比 */ Double humidity,
            /* 最近请求是否失败 */ boolean stale,
            /* 不含隐私数据的状态提示 */ String message,
            /* 10米风速，km/h；旧缓存或未采集时为空 */ Double windSpeed) {}

    /** 日期估算展示结果，无医学结论。 */
    public record Pregnancy(
            /* 孕周或待设置 */ String gestation,
            /* 预产期倒计时说明 */ String countdown) {}
}
