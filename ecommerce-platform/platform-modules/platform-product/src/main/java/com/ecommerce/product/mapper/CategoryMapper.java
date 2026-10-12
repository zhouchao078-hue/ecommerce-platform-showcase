package com.ecommerce.product.mapper;
import com.ecommerce.product.model.PdCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<PdCategory> {
}
