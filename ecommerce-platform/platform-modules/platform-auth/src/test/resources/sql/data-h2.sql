-- H2 Test Data Initialization
INSERT INTO sys_role (name, code, description) VALUES ('User', 'ROLE_USER', 'Regular user role');
INSERT INTO sys_role (name, code, description) VALUES ('Admin', 'ROLE_ADMIN', 'Administrator role');

INSERT INTO sys_user (username, password_hash, phone, email, status) VALUES
('testuser', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13800138000', 'test@example.com', 1),
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900139000', 'admin@example.com', 1),
('disabled_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13700137000', 'disabled@example.com', 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);
INSERT INTO sys_user_role (user_id, role_id) VALUES (3, 1);
