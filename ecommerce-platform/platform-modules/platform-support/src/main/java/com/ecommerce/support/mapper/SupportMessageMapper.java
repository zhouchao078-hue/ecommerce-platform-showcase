package com.ecommerce.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.support.model.SupportMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupportMessageMapper extends BaseMapper<SupportMessage> {
}
