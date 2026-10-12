package com.ecommerce.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.merchant.model.MdMerchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MdMerchantMapper extends BaseMapper<MdMerchant> {
    // 不用写任何方法，BaseMapper 已经自带了：
    // selectOne()、selectById()、insert()、updateById()、deleteById()、selectList()
}
