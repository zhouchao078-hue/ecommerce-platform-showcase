package com.ecommerce.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 元对象处理器配置。
 * <p>
 * 实现 MetaObjectHandler 接口，在插入和更新操作时自动填充公共字段。
 * 配合实体类中 @TableField 注解的 fill 属性使用：
 * <ul>
 *   <li>FieldFill.INSERT: 仅在插入时自动填充（如 createTime）</li>
 *   <li>FieldFill.INSERT_UPDATE: 在插入和更新时均自动填充（如 updateTime）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    /**
     * 插入操作时自动填充字段。
     * <p>
     * 在实体插入数据库前，自动设置 createTime 和 updateTime 为当前时间。
     * 字段名需与实体类中的属性名一致。
     * </p>
     *
     * @param metaObject 元对象，用于获取和设置实体字段值
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("执行插入填充: 设置 createTime 和 updateTime");
        LocalDateTime now = LocalDateTime.now();

        // 填充创建时间字段，仅当字段存在且有对应的 setter 方法
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);

        // 填充更新时间字段，仅当字段存在且有对应的 setter 方法
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新操作时自动填充字段。
     * <p>
     * 在实体更新数据库前，自动设置 updateTime 为当前时间。
     * 字段名需与实体类中的属性名一致。
     * </p>
     *
     * @param metaObject 元对象，用于获取和设置实体字段值
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("执行更新填充: 设置 updateTime");
        LocalDateTime now = LocalDateTime.now();

        // 填充更新时间字段，仅当字段存在且有对应的 setter 方法
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    }
}
