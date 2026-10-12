package com.ecommerce.merchant.service;

public interface FollowService {

    /** 关注（幂等：已关注时不重复插入） */
    void follow(Long userId, int targetType, Long targetId);

    /** 取消关注（幂等：未关注时静默返回） */
    void unfollow(Long userId, int targetType, Long targetId);

    /** 是否已关注；未登录（userId 为 null）返回 false */
    boolean isFollowing(Long userId, int targetType, Long targetId);

    /** 某对象的关注人数 */
    long countFollowers(int targetType, Long targetId);

    /** 我关注的对象的 ID 列表（按关注时间倒序） */
    java.util.List<Long> listTargetIds(Long userId, int targetType);

    /** 某对象的关注者用户 ID 列表 */
    java.util.List<Long> listFollowerIds(int targetType, Long targetId);
}
