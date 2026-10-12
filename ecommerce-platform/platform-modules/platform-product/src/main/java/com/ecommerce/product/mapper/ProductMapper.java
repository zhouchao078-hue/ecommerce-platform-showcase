package com.ecommerce.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.model.PdProduct;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<PdProduct> {

}