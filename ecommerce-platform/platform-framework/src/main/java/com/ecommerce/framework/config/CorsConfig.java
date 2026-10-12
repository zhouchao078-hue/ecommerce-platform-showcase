package com.ecommerce.framework.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

/**
 * CORS 跨域配置类。
 * <p>
 * 实现 WebMvcConfigurer 接口，配置全局跨域资源共享（CORS）规则，
 * 允许来自任意源的跨域请求，以支持前后端分离的开发模式。
 * 同时配置 Jackson 的日期序列化格式和时区。
 * </p>
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 日期时间格式化模式
     */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 配置 CORS 跨域映射规则。
     * <p>
     * 当前配置为开发环境友好模式：允许所有来源、所有方法和所有请求头，
     * 并启用凭据传递（allowCredentials）。生产环境中应限制具体的允许来源。
     * </p>
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                         // 允许所有路径
                .allowedOriginPatterns("*")                // 允许所有来源（使用模式匹配以配合 credentials）
                .allowedMethods("GET", "POST", "PUT",      // 允许指定的 HTTP 方法
                        "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")                       // 允许所有请求头
                .allowCredentials(true)                    // 允许发送 Cookie 和认证信息
                .maxAge(3600);                             // 预检请求缓存 1 小时
        log.info("CORS 跨域配置已加载: 允许所有来源");
    }

    /**
     * 配置 Jackson HTTP 消息转换器。
     * <p>
     * 设置日期格式为 "yyyy-MM-dd HH:mm:ss"，时区为 GMT+8（北京时间），
     * 并禁用将日期序列化为时间戳的功能，使其序列化为可读的日期字符串。
     * 同时注册 Java 8 时间模块以支持 LocalDateTime 等新日期类型的序列化。
     * </p>
     *
     * @param converters HTTP 消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                ObjectMapper objectMapper = jacksonConverter.getObjectMapper();

                // 设置日期格式
                objectMapper.setDateFormat(
                        new SimpleDateFormat(DATE_TIME_PATTERN));

                // 设置时区为 GMT+8（北京时间）
                objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));

                // 禁用将日期序列化为时间戳
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                // 忽略未知属性，防止反序列化时因多余字段报错
                objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

                // 注册 Java 8 时间模块，支持 LocalDateTime 等新日期类型
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
                javaTimeModule.addSerializer(LocalDateTime.class,
                        new LocalDateTimeSerializer(formatter));
                objectMapper.registerModule(javaTimeModule);

                log.info("Jackson 日期格式已配置: pattern={}, timezone=GMT+8", DATE_TIME_PATTERN);
            }
        }
    }
}
