-- ============================================
-- 测试数据种子脚本
-- 适用场景：本地开发、JMeter 压测、功能验证
-- 前置条件：已执行 sql/schema/init_core_tables.sql
-- 密码：123456（BCrypt 密文）
-- ============================================

SET NAMES utf8mb4;

-- 清理已有测试数据（按依赖逆序）
DELETE FROM `ai_import_task` WHERE `user_id` = 1 OR `bank_id` IN (1, 2, 3);
DELETE FROM `wrong_question` WHERE `user_id` = 1;
DELETE FROM `question` WHERE `question_bank_id` IN (1, 2, 3);
DELETE FROM `bank_node` WHERE `user_id` = 1;
DELETE FROM `sys_user` WHERE `id` = 1;

-- 重置自增（可选，确保数据从 1 开始）
ALTER TABLE `sys_user` AUTO_INCREMENT = 1;
ALTER TABLE `bank_node` AUTO_INCREMENT = 1;
ALTER TABLE `question` AUTO_INCREMENT = 1;
ALTER TABLE `wrong_question` AUTO_INCREMENT = 1;
ALTER TABLE `ai_import_task` AUTO_INCREMENT = 1;

-- ============================================
-- 1. 测试用户
-- ============================================
INSERT INTO `sys_user` (`id`, `username`, `password_hash`, `email`, `nickname`, `role`, `email_verified_at`, `create_time`, `update_time`)
VALUES (1, 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'testuser@example.com', '测试同学', 'PREMIUM', NOW(), NOW(), NOW());

-- ============================================
-- 2. 题库树节点（LEAF = 可挂题题库）
-- ============================================

-- 节点 A：公开热点题库（压测靶心，is_public=1）
INSERT INTO `bank_node` (`id`, `user_id`, `parent_id`, `node_kind`, `title`, `description`, `is_public`, `sort_no`, `question_count`, `create_time`, `update_time`)
VALUES (1, 1, NULL, 'LEAF', '2026计算机网络期末必刷题',
        '覆盖 OSI 七层模型、TCP/UDP、HTTP 协议、网络安全等核心考点，共 15 道精选试题。',
        1, 0, 15, NOW(), NOW());

-- 节点 B：公开题库（另一个公开题库，用于验证多题库场景）
INSERT INTO `bank_node` (`id`, `user_id`, `parent_id`, `node_kind`, `title`, `description`, `is_public`, `sort_no`, `question_count`, `create_time`, `update_time`)
VALUES (2, 1, NULL, 'LEAF', '数据结构与算法基础题库',
        '包含数组、链表、栈、队列、树、图、排序算法等经典题型。',
        1, 0, 5, NOW(), NOW());

-- 节点 C：私有题库（验证权限隔离）
INSERT INTO `bank_node` (`id`, `user_id`, `parent_id`, `node_kind`, `title`, `description`, `is_public`, `sort_no`, `question_count`, `create_time`, `update_time`)
VALUES (3, 1, NULL, 'LEAF', '高等数学（上）错题重刷集', '个人整理的高数易错题，仅供自己复习使用。', 0, 0, 2, NOW(), NOW());

-- ============================================
-- 3. 试题 — 题库 A（计算机网络，15 题）
-- ============================================

-- 单选题 1
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        '在 OSI 七层模型中，哪一层负责路由选择与拥塞控制？',
        '["物理层", "数据链路层", "网络层", "传输层"]',
        '["C"]',
        '网络层负责分组转发、路由选择与拥塞控制。IP 协议即工作在这一层。',
        1, NOW(), NOW());

-- 单选题 2
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'TCP 协议通过什么机制保证可靠传输？',
        '["仅通过校验和", "确认应答 + 超时重传", "仅通过序列号", "通过 IP 层的可靠传输"]',
        '["B"]',
        'TCP 通过序列号、确认应答（ACK）、超时重传、流量控制和拥塞控制等机制共同保证可靠传输。',
        2, NOW(), NOW());

-- 单选题 3
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'HTTP 协议默认使用哪个端口？',
        '["21", "25", "80", "443"]',
        '["C"]',
        'HTTP 默认端口为 80，HTTPS 默认端口为 443。FTP 使用 21，SMTP 使用 25。',
        3, NOW(), NOW());

-- 单选题 4
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        '以下哪个 IP 地址属于私有地址？',
        '["8.8.8.8", "172.16.5.10", "202.112.144.30", "114.114.114.114"]',
        '["B"]',
        '172.16.0.0 ~ 172.31.255.255 是 B 类私有地址段。8.8.8.8 是 Google 公共 DNS，其余为公网地址。',
        4, NOW(), NOW());

-- 单选题 5
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'DNS 服务的主要功能是什么？',
        '["分配 IP 地址", "域名到 IP 地址的解析", "加密数据传输", "检测网络故障"]',
        '["B"]',
        'DNS（Domain Name System）的核心功能是将人类可读的域名（如 www.example.com）解析为机器可路由的 IP 地址。',
        5, NOW(), NOW());

-- 多选题 6
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'MULTI',
        '以下哪些协议工作在传输层？',
        '["TCP", "UDP", "IP", "ICMP"]',
        '["A", "B"]',
        'TCP 和 UDP 是传输层协议。IP 是网络层协议，ICMP 也属于网络层。',
        6, NOW(), NOW());

-- 多选题 7
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'MULTI',
        '以下关于 TCP 三次握手的说法，正确的有哪些？',
        '["第一次握手：客户端发送 SYN 包", "第二次握手：服务端回复 SYN-ACK 包", "第三次握手：客户端发送 ACK 包", "握手完成后直接进入 CLOSED 状态"]',
        '["A", "B", "C"]',
        '三次握手：客户端 SYN → 服务端 SYN-ACK → 客户端 ACK，完成后进入 ESTABLISHED 状态，不是 CLOSED。',
        7, NOW(), NOW());

-- 判断题 8
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'JUDGE',
        'UDP 协议提供面向连接的可靠数据传输服务。',
        '["正确", "错误"]',
        '["F"]',
        'UDP 是无连接的、尽最大努力交付的不可靠传输协议。提供面向连接可靠传输的是 TCP。',
        8, NOW(), NOW());

-- 判断题 9
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'JUDGE',
        'ARP 协议用于将 IP 地址解析为 MAC 地址。',
        '["正确", "错误"]',
        '["T"]',
        'ARP（Address Resolution Protocol）正是用于在局域网中将 IP 地址映射为对应的硬件 MAC 地址。',
        9, NOW(), NOW());

-- 单选题 10
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'IPv4 地址的总长度是多少位？',
        '["16 位", "32 位", "64 位", "128 位"]',
        '["B"]',
        'IPv4 地址由 32 位二进制组成，通常表示为 4 个十进制数（如 192.168.1.1）。IPv6 地址为 128 位。',
        10, NOW(), NOW());

-- 单选题 11
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'HTTP 状态码 404 表示什么？',
        '["服务器内部错误", "请求的资源未找到", "请求未授权", "请求成功"]',
        '["B"]',
        '404 Not Found 表示服务器无法找到请求的资源。500 是服务器内部错误，401 是未授权，200 是成功。',
        11, NOW(), NOW());

-- 单选题 12
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        '在浏览器中输入网址并回车后，最先发生的第一步是什么？',
        '["建立 TCP 连接", "DNS 解析域名", "发送 HTTP 请求", "渲染页面"]',
        '["B"]',
        '浏览器首先检查本地缓存，若未命中则向 DNS 服务器发起域名解析请求，获得 IP 地址后才进行 TCP 连接等后续步骤。',
        12, NOW(), NOW());

-- 多选题 13
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'MULTI',
        '以下哪些是应用层协议？',
        '["HTTP", "FTP", "SMTP", "TCP"]',
        '["A", "B", "C"]',
        'HTTP、FTP、SMTP 都属于应用层协议。TCP 是传输层协议，为应用层提供传输服务。',
        13, NOW(), NOW());

-- 判断题 14
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'JUDGE',
        '子网掩码 255.255.255.0 表示前 24 位为网络地址部分。',
        '["正确", "错误"]',
        '["T"]',
        '子网掩码 255.255.255.0（即 /24）表示 IP 地址的前 24 位用于标识网络，后 8 位用于标识主机。',
        14, NOW(), NOW());

-- 单选题 15
INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (1, 'SINGLE',
        'TCP 头部中用于流量控制的字段是什么？',
        '["序列号", "确认号", "窗口大小", "校验和"]',
        '["C"]',
        '窗口大小（Window Size）字段用于 TCP 流量控制，告知发送方接收方当前可用的接收缓冲区大小。',
        15, NOW(), NOW());

-- ============================================
-- 4. 试题 — 题库 B（数据结构与算法，5 题）
-- ============================================

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (2, 'SINGLE',
        '在单链表中删除一个节点，需要修改几个指针？',
        '["0 个", "1 个", "2 个", "视位置而定"]',
        '["B"]',
        '删除单链表中某个节点，只需将其前驱节点的 next 指针指向被删节点的后继即可，修改 1 个指针。',
        1, NOW(), NOW());

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (2, 'SINGLE',
        '快速排序的平均时间复杂度是？',
        '["O(n)", "O(n log n)", "O(n²)", "O(log n)"]',
        '["B"]',
        '快速排序平均时间复杂度为 O(n log n)，最坏情况为 O(n²)（如每次划分极不均衡）。',
        2, NOW(), NOW());

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (2, 'SINGLE',
        '栈的特点是？',
        '["先进先出", "先进后出", "随机存取", "按值存取"]',
        '["B"]',
        '栈是 LIFO（Last In First Out）结构，即先进后出。队列才是 FIFO（First In First Out）。',
        3, NOW(), NOW());

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (2, 'SINGLE',
        '二叉搜索树中，左子树所有节点的值均满足什么条件？',
        '["大于根节点值", "小于根节点值", "等于根节点值", "无特定关系"]',
        '["B"]',
        '二叉搜索树（BST）的性质：左子树所有节点值 < 根节点值 < 右子树所有节点值。',
        4, NOW(), NOW());

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (2, 'SINGLE',
        '广度优先搜索（BFS）通常使用什么数据结构实现？',
        '["栈", "队列", "堆", "哈希表"]',
        '["B"]',
        'BFS 使用队列逐层遍历。DFS 使用栈（或递归）实现深度优先。',
        5, NOW(), NOW());

-- ============================================
-- 5. 试题 — 题库 C（私有高数题库，2 题）
-- ============================================

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (3, 'SINGLE',
        '函数 f(x) = x² 在 x=1 处的导数 f\'(1) 等于？',
        '["0", "1", "2", "3"]',
        '["C"]',
        'f\'(x) = 2x，代入 x=1 得 f\'(1) = 2。',
        1, NOW(), NOW());

INSERT INTO `question` (`question_bank_id`, `question_type`, `stem`, `options_json`, `answer_json`, `analysis`, `sort_no`, `create_time`, `update_time`)
VALUES (3, 'SINGLE',
        '∫₀¹ x dx 的值为？',
        '["0", "0.5", "1", "2"]',
        '["B"]',
        '∫ x dx = x²/2，从 0 到 1 代入得 1²/2 - 0²/2 = 0.5。',
        2, NOW(), NOW());

-- ============================================
-- 6. 错题本 — 模拟 testuser 做错了 3 道题
-- ============================================

-- 做错计算机网络第 1 题 1 次
INSERT INTO `wrong_question` (`user_id`, `question_id`, `wrong_count`, `last_wrong_time`, `create_time`, `update_time`)
VALUES (1, 1, 1, NOW(), NOW(), NOW());

-- 做错计算机网络第 4 题 2 次（反复错同一题）
INSERT INTO `wrong_question` (`user_id`, `question_id`, `wrong_count`, `last_wrong_time`, `create_time`, `update_time`)
VALUES (1, 4, 2, NOW(), NOW(), NOW());

-- 做错数据结构第 1 题 1 次（题库 B 首题 id=16）
INSERT INTO `wrong_question` (`user_id`, `question_id`, `wrong_count`, `last_wrong_time`, `create_time`, `update_time`)
VALUES (1, 16, 1, NOW(), NOW(), NOW());
