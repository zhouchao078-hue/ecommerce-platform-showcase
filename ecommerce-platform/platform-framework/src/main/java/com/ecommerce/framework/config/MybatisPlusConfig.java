package com.ecommerce.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类。
 * <p>
 * 配置 MyBatis Plus 的分页插件，指定数据库类型为 MySQL。
 * 同时通过 @MapperScan 扫描指定包路径下的所有 Mapper 接口，
 * 支持多模块项目的 Mapper 自动发现。
 * </p>
 */
@Configuration
@MapperScan("com.ecommerce.**.mapper")
public class MybatisPlusConfig {

    /**
     * 创建 MyBatis Plus 拦截器 Bean，注册分页插件。
     * <p>
     * 分页插件使用 PaginationInnerInterceptor，数据库类型设置为 MySQL。
     * 如果项目使用其他数据库（如 MySQL），可在此处修改 DbType 为 DbType.MYSQL。
     * 对于多数据库场景，可注册多个 PaginationInnerInterceptor 实例。
     * </p>
     *
     * @return 配置了分页插件的 MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 注册分页插件，指定数据库类型为 MySQL
        PaginationInnerInterceptor paginationInnerInterceptor =
                new PaginationInnerInterceptor(DbType.MYSQL);

        // 设置单页最大查询条数限制，防止恶意全表查询（-1 表示不限制）
        paginationInnerInterceptor.setMaxLimit(500L);

        // 溢出处理：当请求的页码超过总页数时，自动返回第一页
        paginationInnerInterceptor.setOverflow(true);

        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }
}
