package com.goodbird.cnpcefaddon.common;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * 抑制第三方模组的高频无价值日志，只针对两条已实测确认的噪音来源：
 * <ul>
 *   <li>史诗战斗 {@code SkillDataKey.java:51} 的 {@code "Data keys [...] for ..."}（INFO）：
 *       技能注册表重建时把每个技能的数据键完整打印一遍，装的技能模组越多刷得越长；</li>
 *   <li>坚不可摧的 {@code "xxx can't be recognized"}（WARN，共 25 处调用点，如
 *       {@code AnimationMotionSet.java:52}）：解析数据包时对每个未识别的可选字段各打一条。</li>
 * </ul>
 * 两者都不代表故障，但会把日志淹掉。
 * <p>
 * 判定同时要求 <b>logger 名匹配</b> 与 <b>消息特征匹配</b>，因此不会误伤这两个模组的其他日志；
 * 未安装对应模组时不会有该 logger 名的日志，规则自然不触发。
 * ERROR 级一律放行（史诗战斗的 {@code Datapack animation reading failed} 等属真实数据包问题，
 * 必须保留）。
 * <p>
 * 作为 Log4j 全局过滤器安装，仅在配置开启时生效；配置尚未加载时放行，避免早期日志被静默吞掉。
 */
public final class LogSuppressor extends AbstractFilter {
    /** 史诗战斗技能数据键转储的消息特征。 */
    private static final String EPICFIGHT_DATA_KEYS = "Data keys ";
    /** 坚不可摧未识别字段告警的消息特征（对原始模板与格式化结果都成立）。 */
    private static final String INDESTRUCTIBLE_UNRECOGNIZED = "can't be recognized";

    private static final String EPICFIGHT_LOGGER = "epicfight";
    private static final String INDESTRUCTIBLE_LOGGER = "indestructible";

    private static boolean installed;

    private LogSuppressor() {
        super(Result.NEUTRAL, Result.NEUTRAL);
    }

    /** 在 Log4j 根配置上安装一次全局过滤器。重复调用无副作用。 */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            LogSuppressor filter = new LogSuppressor();

            // 必须挂到 LoggerConfig 上：Configuration.addFilter 只作用于 Configuration 自身，
            // 不参与 logger 的过滤链（已实测 denied 恒为 0）。挂根 LoggerConfig 才对所有 logger 生效。
            configuration.getRootLogger().addFilter(filter);

            // 这两个模组若已在配置中拥有独立的 LoggerConfig，则不会继承根节点的过滤器，需分别挂上。
            for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                if (loggerConfig != configuration.getRootLogger()
                        && (EPICFIGHT_LOGGER.equals(loggerConfig.getName())
                            || INDESTRUCTIBLE_LOGGER.equals(loggerConfig.getName()))) {
                    loggerConfig.addFilter(filter);
                }
            }

            filter.start();
            context.updateLoggers();
        } catch (Throwable t) {
            // 日志抑制属可选增强：任何 Log4j 内部结构差异都不应影响模组加载。
            installed = false;
        }
    }

    /**
     * @param loggerName Log4j logger 名（史诗战斗与坚不可摧均以 MODID 作为 logger 名）
     * @param message    原始消息模板或其字符串形式
     */
    private Result evaluate(String loggerName, Level level, String message) {
        if (loggerName == null || message == null || level == null) {
            return Result.NEUTRAL;
        }

        // 真实错误一律保留。
        if (level.isMoreSpecificThan(Level.ERROR)) {
            return Result.NEUTRAL;
        }

        if (EPICFIGHT_LOGGER.equals(loggerName)
                && message.startsWith(EPICFIGHT_DATA_KEYS)
                && isEnabled(AddonConfig.SUPPRESS_EPICFIGHT_LOG)) {
            return Result.DENY;
        }

        if (INDESTRUCTIBLE_LOGGER.equals(loggerName)
                && message.contains(INDESTRUCTIBLE_UNRECOGNIZED)
                && isEnabled(AddonConfig.SUPPRESS_INDESTRUCTIBLE_LOG)) {
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    /**
     * 配置未加载完成时返回 false（放行）。此时读取 ForgeConfigSpec 会抛异常，
     * 且早期日志本就该完整保留。
     */
    private static boolean isEnabled(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        try {
            if (!AddonConfig.SPEC.isLoaded()) {
                return false;
            }
            return value.get();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String nameOf(Logger logger) {
        return logger == null ? null : logger.getName();
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return evaluate(nameOf(logger), level, msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return evaluate(nameOf(logger), level, msg == null ? null : msg.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return evaluate(nameOf(logger), level, msg == null ? null : msg.getFormat());
    }

    @Override
    public Result filter(org.apache.logging.log4j.core.LogEvent event) {
        if (event == null) {
            return Result.NEUTRAL;
        }
        Message message = event.getMessage();
        return evaluate(event.getLoggerName(), event.getLevel(),
                message == null ? null : message.getFormat());
    }
}
