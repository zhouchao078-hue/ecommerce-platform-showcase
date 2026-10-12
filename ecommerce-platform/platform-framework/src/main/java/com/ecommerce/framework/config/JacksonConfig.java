package com.ecommerce.framework.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson JSON 序列化/反序列化配置类。
 * <p>
 * 通过 Jackson2ObjectMapperBuilderCustomizer 对全局 ObjectMapper 进行定制：
 * <ul>
 *   <li>设置日期格式化模式为 "yyyy-MM-dd HH:mm:ss"</li>
 *   <li>设置时区为 GMT+8（北京时间）</li>
 *   <li>禁用日期序列化为时间戳（WRITE_DATES_AS_TIMESTAMPS）</li>
 *   <li>注册 Java 8 时间模块，支持 LocalDateTime / LocalDate / LocalTime 的序列化与反序列化</li>
 *   <li>忽略未知 JSON 属性，防止反序列化时因多余字段报错</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * 日期时间格式化模式
     */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式化模式
     */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 时间格式化模式
     */
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 创建 Jackson2ObjectMapperBuilderCustomizer Bean。
     * <p>
     * Spring Boot 会自动装配该 Bean 并应用到全局的 ObjectMapper，
     * 覆盖默认的序列化/反序列化行为。
     * </p>
     *
     * @return 配置好的自定义器实例
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 设置日期格式化模式
            builder.dateFormat(new SimpleDateFormat(DATE_TIME_PATTERN));

            // 设置时区为 GMT+8（北京时间）
            builder.timeZone(TimeZone.getTimeZone("GMT+8").getID());

            // 禁用将日期序列化为时间戳
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // 忽略未知属性
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // 构建 Java 8 时间模块，注册各种日期时间类型的序列化器和反序列化器
            JavaTimeModule javaTimeModule = new JavaTimeModule();

            // 注册 LocalDateTime 的序列化器和反序列化器
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(dateTimeFormatter));
            javaTimeModule.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(dateTimeFormatter));

            // 注册 LocalDate 的序列化器和反序列化器
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
            javaTimeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(dateFormatter));
            javaTimeModule.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(dateFormatter));

            // 注册 LocalTime 的序列化器和反序列化器
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);
            javaTimeModule.addSerializer(LocalTime.class,
                    new LocalTimeSerializer(timeFormatter));
            javaTimeModule.addDeserializer(LocalTime.class,
                    new LocalTimeDeserializer(timeFormatter));

            // 注册 Java 8 时间模块到 ObjectMapper
            builder.modules(javaTimeModule);

            log.info("Jackson 全局配置已加载: dateFormat={}, timezone=GMT+8", DATE_TIME_PATTERN);
        };
    }
}
