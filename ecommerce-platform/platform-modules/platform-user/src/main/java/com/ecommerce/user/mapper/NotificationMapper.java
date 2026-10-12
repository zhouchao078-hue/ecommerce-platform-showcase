package com.ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.user.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Insert("""
            <script>
            INSERT IGNORE INTO ud_notification
                (user_id, type, title, content, ref_type, ref_id, is_read, dedup_key, created_at)
            VALUES
            <foreach collection="userIds" item="userId" separator=",">
                (#{userId}, #{type}, #{title}, #{content}, #{refType}, #{refId}, 0, #{dedupKey}, NOW())
            </foreach>
            </script>
            """)
    int insertIgnoreBatch(@Param("userIds") List<Long> userIds,
                          @Param("type") int type,
                          @Param("title") String title,
                          @Param("content") String content,
                          @Param("refType") int refType,
                          @Param("refId") Long refId,
                          @Param("dedupKey") String dedupKey);
}
