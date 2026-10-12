package com.ecommerce.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.merchant.mapper.FollowMapper;
import com.ecommerce.merchant.model.UdFollow;
import com.ecommerce.merchant.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;

    @Override
    public void follow(Long userId, int targetType, Long targetId) {
        if (isFollowing(userId, targetType, targetId)) {
            return;
        }
        UdFollow follow = new UdFollow();
        follow.setUserId(userId);
        follow.setTargetType(targetType);
        follow.setTargetId(targetId);
        follow.setCreateTime(LocalDateTime.now());
        followMapper.insert(follow);
        log.info("关注成功: userId={}, type={}, targetId={}", userId, targetType, targetId);
    }

    @Override
    public void unfollow(Long userId, int targetType, Long targetId) {
        QueryWrapper<UdFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("target_type", targetType)
                .eq("target_id", targetId);
        followMapper.delete(wrapper);
        log.info("取消关注: userId={}, type={}, targetId={}", userId, targetType, targetId);
    }

    @Override
    public boolean isFollowing(Long userId, int targetType, Long targetId) {
        if (userId == null) {
            return false;
        }
        QueryWrapper<UdFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("target_type", targetType)
                .eq("target_id", targetId);
        return followMapper.selectCount(wrapper) > 0;
    }

    @Override
    public long countFollowers(int targetType, Long targetId) {
        QueryWrapper<UdFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", targetType).eq("target_id", targetId);
        return followMapper.selectCount(wrapper);
    }

    @Override
    public List<Long> listTargetIds(Long userId, int targetType) {
        QueryWrapper<UdFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("target_type", targetType)
                .orderByDesc("created_at");
        return followMapper.selectList(wrapper).stream()
                .map(UdFollow::getTargetId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> listFollowerIds(int targetType, Long targetId) {
        QueryWrapper<UdFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", targetType)
                .eq("target_id", targetId)
                .orderByAsc("id");
        return followMapper.selectList(wrapper).stream()
                .map(UdFollow::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }
}
