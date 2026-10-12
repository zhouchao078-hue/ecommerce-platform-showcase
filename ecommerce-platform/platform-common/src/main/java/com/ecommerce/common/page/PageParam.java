package com.ecommerce.common.page;

/**
 * 分页查询请求参数
 *
 * <p>Controller接收分页查询请求时使用此类封装前端传入的分页参数。
 * 提供合理的默认值：pageNum=1, pageSize=10, sortOrder="desc"。
 */
public class PageParam {

    /** 当前页码，默认第1页 */
    private int pageNum = 1;

    /** 每页显示条数，默认10条 */
    private int pageSize = 10;

    /** 排序方式，asc为升序，desc为降序，默认降序 */
    private String sortOrder = "desc";

    /**
     * 计算数据库查询的偏移量
     *
     * @return 偏移量，即从第几条记录开始查询
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    // ========== Getters & Setters ==========

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

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
