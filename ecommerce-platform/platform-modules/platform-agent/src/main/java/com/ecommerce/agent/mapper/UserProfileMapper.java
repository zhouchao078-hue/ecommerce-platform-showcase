package com.ecommerce.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.agent.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
