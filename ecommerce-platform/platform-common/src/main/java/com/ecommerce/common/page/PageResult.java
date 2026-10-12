package com.ecommerce.common.page;

import java.util.List;

/**
 * 分页查询结果封装
 *
 * <p>用于封装分页查询的返回结果，包含当前页的记录列表以及分页元信息。
 * 通过静态工厂方法 {@link #of(List, long, int, int)} 便捷构建实例。
 *
 * @param <T> 记录数据类型
 */
public class PageResult<T> {

    /** 当前页的记录列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int pageNum;

    /** 每页显示条数 */
    private int pageSize;

    /** 无参构造 */
    public PageResult() {
    }

    /** 全参构造 */
    public PageResult(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * 静态工厂方法，构建分页结果
     *
     * @param records  当前页记录列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      记录数据类型
     * @return 分页结果实例
     */
    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    // ========== Getters & Setters ==========

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
