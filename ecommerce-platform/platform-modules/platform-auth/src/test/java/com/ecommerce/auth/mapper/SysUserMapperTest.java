package com.ecommerce.auth.mapper;

import com.ecommerce.auth.model.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 用户 Mapper 层单元测试
 * 使用 H2 内存数据库
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("SysUserMapper 测试")
class SysUserMapperTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Test
    @DisplayName("根据用户名查询用户 - 用户名存在时应返回用户")
    void testSelectByUsername_Found() {
        SysUser user = sysUserMapper.selectByUsername("testuser");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("13800138000", user.getPhone());
        assertEquals("test@example.com", user.getEmail());
        assertEquals(1, user.getStatus());
    }

    @Test
    @DisplayName("根据用户名查询用户 - 用户名不存在时应返回 null")
    void testSelectByUsername_NotFound() {
        SysUser user = sysUserMapper.selectByUsername("nonexistent_user");
        assertNull(user);
    }

    @Test
    @DisplayName("根据手机号码查询用户 - 手机号存在时应返回用户")
    void testSelectByPhone_Found() {
        SysUser user = sysUserMapper.selectByPhone("13800138000");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("13800138000", user.getPhone());
    }

    @Test
    @DisplayName("根据手机号码查询用户 - 手机号不存在时应返回 null")
    void testSelectByPhone_NotFound() {
        SysUser user = sysUserMapper.selectByPhone("00000000000");
        assertNull(user);
    }

    @Test
    @DisplayName("插入新用户并查询验证")
    void testInsertAndSelectById() {
        SysUser newUser = new SysUser();
        newUser.setUsername("newuser");
        newUser.setPasswordHash("hashed_password");
        newUser.setPhone("13600136000");
        newUser.setEmail("new@example.com");
        newUser.setStatus(1);

        int rows = sysUserMapper.insert(newUser);
        assertEquals(1, rows);
        assertNotNull(newUser.getId());

        SysUser inserted = sysUserMapper.selectById(newUser.getId());
        assertNotNull(inserted);
        assertEquals("newuser", inserted.getUsername());
        assertEquals("13600136000", inserted.getPhone());
    }

    @Test
    @DisplayName("插入用户后可通过 selectByUsername 查找到")
    void testInsertAndSelectByUsername() {
        SysUser newUser = new SysUser();
        newUser.setUsername("findme");
        newUser.setPasswordHash("hashed_password");
        newUser.setPhone("13500135000");
        newUser.setStatus(1);

        sysUserMapper.insert(newUser);

        SysUser found = sysUserMapper.selectByUsername("findme");
        assertNotNull(found);
        assertEquals("findme", found.getUsername());
    }
}
