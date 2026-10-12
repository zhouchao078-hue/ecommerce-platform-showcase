package com.ecommerce.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.cart.model.Cart;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}
