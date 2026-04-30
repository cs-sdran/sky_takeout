
# sky-take-out
更新于2026-4-26
sky-take-out        整个项目根目录 / Maven 父工程

.idea              IDEA 工具配置目录，不是业务代码

sky-common         公共模块，放工具类、常量、异常、统一返回结果等

sky-pojo           实体类模块，放 Entity、DTO、VO 等数据对象

sky-server         服务端模块，放 Controller、Service、Mapper、启动类等核心业务代码

.gitignore         Git 忽略配置，控制哪些文件不提交

pom.xml            Maven 配置文件，管理依赖、模块、插件、版本

4.27
DTO 和 Entity 的区别
这张表很重要。

类型	中文含义	    主要用途	                是否对应数据库表
DTO	    数据传输对象	接收前端参数、层间传参	    不一定
Entity / PO	实体对象	对应数据库表	                通常对应
VO	    视图对象	     返回给前端展示	            不一定
BO	    业务对象	    封装复杂业务逻辑数据	        不一定

@RequestMapping 的作用是：
把浏览器或前端发来的 HTTP 请求，映射到指定的 Controller 类或方法上。
简单说，它用来定义接口路径。

4.30
新增员工模块开发完成

功能说明：
- 接口路径：POST /admin/employee
- 功能描述：管理员新增员工账号
- 默认密码：123456（MD5加密存储）
- 默认状态：启用（status=1）

开发步骤：
1. EmployeeMapper接口 → 添加insert方法（使用@Insert注解）
2. EmployeeService接口 → 添加save(EmployeeDTO)方法
3. EmployeeServiceImpl → 实现save业务逻辑
   - DTO转Entity（BeanUtils.copyProperties）
   - 设置默认状态为启用
   - 设置默认密码并MD5加密
   - 设置创建时间、更新时间
   - 从BaseContext获取当前登录用户ID，设置createUser和updateUser
   - 调用Mapper插入数据库
4. EmployeeController → 添加@PostMapping("/")接口

关键代码位置：
- Mapper: sky-server/src/main/java/com/sky/mapper/EmployeeMapper.java
- Service接口: sky-server/src/main/java/com/sky/service/EmployeeService.java
- Service实现: sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java
- Controller: sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java
