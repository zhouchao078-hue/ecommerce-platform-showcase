package com.ecommerce.feed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.feed.model.FeedPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 广场动态 Mapper
 */
@Mapper
public interface FeedPostMapper extends BaseMapper<FeedPost> {
}
