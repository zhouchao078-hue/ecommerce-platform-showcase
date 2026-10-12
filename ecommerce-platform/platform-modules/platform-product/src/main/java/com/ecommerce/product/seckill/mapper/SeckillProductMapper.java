package com.ecommerce.product.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.seckill.model.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillProductMapper extends BaseMapper<SeckillProduct> {
    @Update("UPDATE seckill_product SET stock = stock - 1, version = version + 1 "
            + "WHERE id = #{id} AND stock > 0 AND status = 1")
    int decrementStock(Long id);
}
