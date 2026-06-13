（二）图书借阅模块测试用例
测试用例 ID：B001
测试场景：正常借阅（满足所有条件）
测试数据：读者学号：2021001；图书编号：9787111111111；借阅数量：1；读者状态：无未缴罚款，未达最大借阅数；图书状态：库存 > 0
预期结果：借阅申请插入成功，状态为 “待审核”
涉及文件：`library-frontend/src/views/BookBrowsing.vue` (前端申请), `springboot8393c/src/main/java/com/controller/TushujieyueController.java` (后端接口)

测试用例 ID：B002
测试场景：图书库存不足
测试数据：读者学号：2021001；图书编号：9787111111111；借阅数量：1；图书状态：库存 = 0
预期结果：提示 “图书已借完”，未插入借阅记录
涉及文件：`springboot8393c/src/main/java/com/controller/TushujieyueController.java` (需增加库存校验逻辑)

测试用例 ID：B003
测试场景：读者存在未缴罚款
测试数据：读者学号：2021001；图书编号：9787222222222；借阅数量：1；读者状态：有未缴罚款（chaoqifakuan.ispay='未支付'）
预期结果：提示 “存在未缴罚款，无法借阅”，未插入记录
涉及文件：`springboot8393c/src/main/java/com/controller/TushujieyueController.java` (需增加罚款校验逻辑)

测试用例 ID：B004
测试场景：达到最大借阅数
测试数据：读者学号：2021001；图书编号：9787222222222；借阅数量：1；读者状态：已借数量 >= 限额
预期结果：提示 “已达最大借阅数”，未插入记录
涉及文件：`springboot8393c/src/main/java/com/controller/TushujieyueController.java` (需增加限额校验逻辑)

测试用例 ID：B005
测试场景：管理员审核通过
测试数据：借阅编号：20230628001；审核状态：是；审核回复：“审核通过”
预期结果：借阅记录状态更新为 “是”，图书库存减 1
涉及文件：`library-frontend/src/views/BorrowApproval.vue` (前端审核), `springboot8393c/src/main/java/com/controller/TushujieyueController.java` (需增加审核通过扣减库存逻辑)

测试用例 ID：B006
测试场景：管理员审核驳回
测试数据：借阅编号：20230628002；审核状态：否；审核回复：“图书已预约”
预期结果：借阅记录状态更新为 “否”，图书库存不变
涉及文件：`library-frontend/src/views/BorrowApproval.vue`

（三）图书归还模块测试用例
测试用例 ID：R001
测试场景：正常归还（未超期）
测试数据：借阅编号：20230628001；预还日期：2023-07-28；实际还书日期：2023-07-20
预期结果：归还记录更新，图书库存加 1，无罚款
涉及文件：`library-frontend/src/views/ReturnApproval.vue` (前端归还), `springboot8393c/src/main/java/com/controller/TushuguihaiController.java` (需增加归还增加库存逻辑)

测试用例 ID：R002
测试场景：超期归还（产生罚款）
测试数据：借阅编号：20230628003；预还日期：2023-06-28；实际还书日期：2023-07-05；超时单价：1 元 / 天
预期结果：归还记录更新，生成罚款（7 元），库存加 1
涉及文件：`library-frontend/src/views/ReturnApproval.vue` (前端计算罚款), `springboot8393c/src/main/java/com/controller/ChaoqifakuanController.java` (生成罚款记录)

测试用例 ID：R003
测试场景：归还不存在的借阅记录
测试数据：借阅编号：9999999999
预期结果：提示 “借阅记录不存在”，无数据变更
涉及文件：`springboot8393c/src/main/java/com/controller/TushuguihaiController.java`

（四）罚款管理模块测试用例
测试用例 ID：P001
测试场景：查询未缴罚款
测试数据：读者学号：2021001
预期结果：返回该读者所有未支付罚款记录
涉及文件：`library-frontend/src/views/MyFines.vue`, `springboot8393c/src/main/java/com/controller/ChaoqifakuanController.java`

测试用例 ID：P002
测试场景：在线支付罚款
测试数据：罚款 ID：71；支付方式：微信支付
预期结果：罚款状态更新为 “已支付”
涉及文件：`library-frontend/src/views/MyFines.vue` (前端模拟支付), `springboot8393c/src/main/java/com/controller/ChaoqifakuanController.java`

测试用例 ID：P003
测试场景：支付已缴罚款
测试数据：罚款 ID：71；当前状态：已支付
预期结果：提示 “该罚款已支付”，无状态变更
涉及文件：`springboot8393c/src/main/java/com/controller/ChaoqifakuanController.java`

测试用例 ID：P004
测试场景：查询支付历史
测试数据：读者学号：2021001
预期结果：返回该读者所有已支付罚款记录
涉及文件：`library-frontend/src/views/MyFines.vue`

（五）用户认证模块测试用例
测试用例 ID：U001
测试场景：读者正常登录
测试数据：学号：2021001；密码：123456
预期结果：登录成功，返回 Token 和角色 “读者”
涉及文件：`library-frontend/src/views/LoginView.vue`, `springboot8393c/src/main/java/com/controller/DuzheController.java`

测试用例 ID：U002
测试场景：管理员正常登录
测试数据：用户名：admin；密码：admin
预期结果：登录成功，返回 Token 和角色 “管理员”
涉及文件：`library-frontend/src/views/LoginView.vue`, `springboot8393c/src/main/java/com/controller/UsersController.java`

测试用例 ID：U003
测试场景：密码错误
测试数据：学号：2021001；密码：654321
预期结果：登录失败，提示 “账号或密码不正确”
涉及文件：`springboot8393c/src/main/java/com/controller/DuzheController.java`

测试用例 ID：U004
测试场景：学号不存在
测试数据：学号：2021099；密码：123456
预期结果：登录失败，提示 “账号或密码不正确”
涉及文件：`springboot8393c/src/main/java/com/controller/DuzheController.java`

测试用例 ID：U005
测试场景：读者注册（信息合法）
测试数据：学号：2021009；姓名：张三；密码：123456；手机：13823888889
预期结果：注册成功
涉及文件：`springboot8393c/src/main/java/com/controller/DuzheController.java`

测试用例 ID：U006
测试场景：学号重复注册
测试数据：学号：2021001；姓名：李四；密码：123456
预期结果：注册失败，提示 “注册用户已存在”
涉及文件：`springboot8393c/src/main/java/com/controller/DuzheController.java`
