# Sky Takeout - 外卖管理系统

## 项目简介
Sky Takeout 是一个基于 Spring Boot 的外卖管理系统，采用前后端分离架构设计。本项目主要实现员工管理模块的核心功能，包括员工登录、新增员工、分页查询、启用/禁用员工账号以及编辑员工信息等功能。

## 技术栈
- **后端框架**: Spring Boot 2.7.3
- **持久层**: MyBatis + PageHelper 分页插件
- **数据库连接池**: Druid
- **JSON处理**: Fastjson
- **API文档**: Knife4j (Swagger增强版)
- **JWT认证**: JJWT
- **工具类库**: Lombok, Commons-lang
- **其他**: AspectJ (AOP), Aliyun OSS (对象存储)

## 项目结构
```
sky-take-out/                # 项目根目录 / Maven 父工程
├── .idea/                   # IDEA 工具配置目录
├── sky-common/              # 公共模块
│   ├── constant/            # 常量定义
│   ├── context/             # 上下文工具类
│   ├── enumeration/         # 枚举类
│   ├── exception/           # 自定义异常类
│   ├── json/                # JSON 处理相关
│   ├── properties/          # 配置属性类
│   ├── result/              # 统一返回结果封装
│   └── utils/               # 工具类
├── sky-pojo/                # 实体类模块
│   ├── dto/                 # 数据传输对象
│   ├── entity/              # 实体类（对应数据库表）
│   └── vo/                  # 视图对象
└── sky-server/              # 服务端模块
    ├── config/              # 配置类
    ├── controller/          # 控制器层
    ├── handler/             # 处理器
    ├── interceptor/         # 拦截器
    ├── mapper/              # 数据访问层
    ├── service/             # 业务逻辑层
    └── SkyApplication.java  # 启动类
```

## 核心功能模块

### 1. 员工管理模块

#### 1.1 员工登录
- **接口路径**: `POST /admin/employee/login`
- **功能描述**: 员工通过用户名和密码登录系统
- **安全机制**: 
  - 密码使用 MD5 加密存储和验证
  - JWT Token 认证机制
  - 账号状态检查（禁用账号无法登录）

#### 1.2 新增员工
- **接口路径**: `POST /admin/employee`
- **功能描述**: 管理员新增员工账号
- **默认设置**:
  - 默认密码: 123456（MD5加密存储）
  - 默认状态: 启用（status=1）
  - 自动记录创建时间和创建人

#### 1.3 员工分页查询
- **接口路径**: `GET /admin/employee/page`
- **功能描述**: 按页码和每页数量查询员工列表
- **请求参数**: page(页码), pageSize(每页大小), name(姓名，可选)
- **返回数据**: 总记录数和员工列表数据
- **技术实现**: 使用 PageHelper 实现物理分页

#### 1.4 启用/禁用员工账号 ⭐ (5月3日完成)
- **接口路径**: `POST /admin/employee/status/{status}`
- **功能描述**: 管理员可以启用或禁用员工账号
- **请求参数**: 
  - status: 状态值（1-启用，0-禁用）
  - id: 员工ID
- **业务规则**:
  - 禁用的账号无法登录系统
  - 状态变更会记录更新时间和更新人

**实现细节**:
```java
// Controller层
@PostMapping("/status/{status}")
@ApiOperation("启用禁用员工账号")
public Result startOrStop(@PathVariable Integer status, Long id) {
    employeeService.startOrStop(status, id);
    return Result.success();
}

// Service层
public void startOrStop(Integer status, Long id) {
    Employee employee = Employee.builder()
                    .status(status)
                    .id(id)
                    .build();
    employeeMapper.update(employee);
}
```

#### 1.5 编辑员工信息 ⭐ (5月3日完成)
- **接口路径**: `PUT /admin/employee`
- **功能描述**: 修改员工基本信息
- **可编辑字段**: 姓名、用户名、手机号、性别、身份证号等
- **安全措施**: 
  - 密码不在编辑接口中修改（有专门的密码修改接口）
  - 返回数据时密码字段脱敏显示（"*****"）
  - 自动记录更新时间和更新人

**实现细节**:
```java
// Controller层
@PutMapping
@ApiOperation("编辑员工信息")
public Result update(@RequestBody EmployeeDTO employeeDTO) {
    employeeService.update(employeeDTO);
    return Result.success();
}

// Service层（使用自动填充后，无需手动设置时间和用户）
public void update(EmployeeDTO employeeDTO) {
    Employee employee = new Employee();
    BeanUtils.copyProperties(employeeDTO, employee);
    // 以下字段由 AOP 自动填充，无需手动设置
    // employee.setUpdateTime(LocalDateTime.now());
    // employee.setUpdateUser(BaseContext.getCurrentId());
    employeeMapper.update(employee);
}

// Mapper层（添加 @AutoFill 注解）
@AutoFill(value = OperationType.UPDATE)
void update(Employee employee);

// 查询时密码脱敏
public Employee getById(Long id) {
   Employee employee = employeeMapper.getById(id);
   employee.setPassword("*****");  // 密码脱敏
   return employee;
}
```

#### 1.6 公共字段自动填充 ⭐⭐⭐ (今日完成)

**功能说明**:
- 使用 AOP 切面技术自动填充审计字段
- 支持 INSERT 和 UPDATE 两种操作类型
- 基于自定义注解和反射机制实现

**核心代码**:

1️⃣ **自定义注解** (`@AutoFill`):
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    OperationType value();  // 操作类型：INSERT 或 UPDATE
}
```

2️⃣ **AOP 切面类** (`AutoFillAspect`):
```java
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    
    // 切入点：拦截所有 Mapper 包下带有 @AutoFill 注解的方法
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        // 1. 获取操作类型（INSERT/UPDATE）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();
        
        // 2. 获取方法参数（实体对象）
        Object[] args = joinPoint.getArgs();
        Object entity = args[0];
        
        // 3. 准备填充数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();
        
        // 4. 根据操作类型填充字段
        if (operationType == OperationType.INSERT) {
            // INSERT: 填充 create_time, update_time, create_user, update_user
            setFieldValue(entity, "setCreateTime", now);
            setFieldValue(entity, "setUpdateTime", now);
            setFieldValue(entity, "setCreateUser", currentId);
            setFieldValue(entity, "setUpdateUser", currentId);
        } else if (operationType == OperationType.UPDATE) {
            // UPDATE: 填充 update_time, update_user
            setFieldValue(entity, "setUpdateTime", now);
            setFieldValue(entity, "setUpdateUser", currentId);
        }
    }
}
```

3️⃣ **Mapper 接口使用示例**:
```java
// 插入操作 - 自动填充4个字段
@Insert("insert into employee (...) values (...)")
@AutoFill(value = OperationType.INSERT)
void insert(Employee employee);

// 更新操作 - 自动填充2个字段
@AutoFill(value = OperationType.UPDATE)
void update(Employee employee);
```

**技术亮点**:
- ✨ **反射机制**: 通过反射动态调用实体的 setter 方法
- ✨ **AOP 切面**: 无侵入式增强，不影响原有业务逻辑
- ✨ **常量管理**: 使用 AutoFillConstant 统一管理方法名
- ✨ **操作类型区分**: 根据 INSERT/UPDATE 填充不同字段
- ✨ **线程安全**: 从 ThreadLocal 获取当前用户ID

**优化效果对比**:

使用前（需要手动设置）:
### java
// Service 层每个方法都要写
employee.setCreateTime(LocalDateTime.now());
employee.setUpdateTime(LocalDateTime.now());
employee.setCreateUser(BaseContext.getCurrentId());
employee.setUpdateUser(BaseContext.getCurrentId());
```

使用后（自动填充）:
```java
// 只需在 Mapper 方法上加注解
@AutoFill(value = OperationType.INSERT)
void insert(Employee employee);
// Service 层无需任何额外代码
```

### 2. 菜品管理模块

#### 2.1 新增菜品 ⭐⭐ (5月5日完成)
- **接口路径**: `POST /admin/dish`
- **功能描述**: 管理员新增菜品信息，包括基本信息和口味数据
- **涉及表**: 
  - `dish`: 菜品基本信息表
  - `dish_flavor`: 菜品口味关系表
- **请求参数**:
  - name: 菜品名称
  - categoryId: 菜品分类ID
  - price: 菜品价格
  - image: 菜品图片URL
  - description: 菜品描述
  - status: 菜品状态（0-停售，1-起售）
  - flavors: 口味列表（可选）
    - name: 口味名称
    - value: 口味数据
- **业务规则**:
  - 使用事务保证数据一致性
  - 先插入菜品基本信息，再批量插入关联的口味数据
  - 自动填充创建时间、更新时间、创建人、更新人等审计字段

**实现细节**:
```java
// Controller层
@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品管理接口")
@Slf4j
public class DishController {
    @Autowired
    private DishService dishService;
    
    /**
     * 新增菜品
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }
}

// Service层
@Service
@Slf4j
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        // 1. 将DTO转换为Entity并插入菜品基本信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);

        // 2. 获取生成的菜品ID
        Long dishId = dish.getId();
        
        // 3. 处理口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            // 设置每个口味的菜品ID
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dishId);
            }
            // 批量插入口味数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }
}

// Mapper层
@Mapper
public interface DishMapper {
    /**
     * 插入菜品数据
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);
}

@Mapper
public interface DishFlavorMapper {
    /**
     * 批量插入口味数据
     * @param flavors
     */
    @AutoFill(value = OperationType.INSERT)
    void insertBatch(List<DishFlavor> flavors);
}
```

**技术亮点**:
- ✨ **事务管理**: 使用@Transactional注解确保菜品和口味数据的一致性
- ✨ **批量操作**: 口味数据采用批量插入提高性能
- ✨ **自动填充**: 利用AOP自动填充审计字段，减少重复代码
- ✨ **数据传输对象**: 使用DishDTO接收前端复杂数据结构
- ✨ **属性拷贝**: 使用BeanUtils简化DTO到Entity的转换

## 关键技术点

### 1. JWT 认证机制
- 使用 JWT Token 进行身份验证
- Token 存储在请求头中
- 通过拦截器验证 Token 有效性
- 从 Token 中解析用户ID并存入 ThreadLocal

### 2. 统一异常处理
- 自定义多种业务异常类
- 全局异常处理器统一捕获和处理异常
- 返回统一的错误信息和状态码

### 3. 动态SQL更新
- 使用 MyBatis 动态SQL实现选择性更新
- 只更新传入的非空字段
- 避免覆盖未修改的字段

### 4. 数据脱敏
- 查询员工信息时对密码字段进行脱敏处理
- 确保敏感信息不会泄露给前端

### 5. 公共字段自动填充 ⭐ (今日完成)
- **技术实现**: 基于 AOP + 自定义注解 + 反射机制
- **自动填充字段**:
  - `create_time`: 创建时间（INSERT操作）
  - `update_time`: 更新时间（INSERT和UPDATE操作）
  - `create_user`: 创建人ID（INSERT操作）
  - `update_user`: 更新人ID（INSERT和UPDATE操作）
- **核心组件**:
  - `@AutoFill` 自定义注解：标识需要自动填充的方法
  - `AutoFillAspect` 切面类：拦截带有 @AutoFill 注解的方法
  - `OperationType` 枚举：区分 INSERT 和 UPDATE 操作
  - `AutoFillConstant` 常量类：定义setter方法名常量
- **工作原理**:
  1. 在 Mapper 方法上添加 `@AutoFill(OperationType.INSERT/UPDATE)` 注解
  2. AOP 切面拦截带有注解的方法执行
  3. 通过反射获取方法参数（实体对象）
  4. 根据操作类型调用对应的 setter 方法填充字段
  5. 从 BaseContext 获取当前登录用户ID
- **优势**:
  - 消除重复代码，无需在每个 Service 中手动设置审计字段
  - 统一管理，确保所有数据操作都正确记录审计信息
  - 提高代码可维护性和一致性

## DTO 和 Entity 的区别

| 类型 | 中文含义 | 主要用途 | 是否对应数据库表 |
|------|---------|---------|----------------|
| DTO | 数据传输对象 | 接收前端参数、层间传参 | 不一定 |
| Entity / PO | 实体对象 | 对应数据库表 | 通常对应 |
| VO | 视图对象 | 返回给前端展示 | 不一定 |
| BO | 业务对象 | 封装复杂业务逻辑数据 | 不一定 |

## 开发进度

### 已完成功能
- ✅ 4月27日: 项目基础架构搭建
- ✅ 4月30日: 员工登录功能
- ✅ 4月30日: 新增员工功能
- ✅ 5月1日: 员工分页查询功能
- ✅ 5月3日: 启用/禁用员工账号功能
- ✅ 5月3日: 编辑员工信息功能
- ✅ 5月4日: 公共字段自动填充功能（AOP实现）
- ✅ 5月5日: 菜品添加功能（含口味管理）

### 待开发功能
- ⏳ 菜品查询功能
- ⏳ 菜品修改功能
- ⏳ 菜品删除功能
- ⏳ 员工删除功能
- ⏳ 密码修改功能
- ⏳ 套餐管理模块
- ⏳ 订单管理模块
- ⏳ 数据统计模块

## 运行环境
- JDK 8+
- Maven 3.6+
- MySQL 5.7+
- Redis (可选，用于缓存)

## 快速开始

1. 克隆项目
```bash
git clone <repository-url>
```

2. 导入数据库
```bash
# 执行 SQL 脚本创建数据库和表
```

3. 修改配置文件
```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sky_take_out?useUnicode=true&characterEncoding=utf-8
    username: root
    password: your_password
```

4. 启动项目
```bash
mvn spring-boot:run
```

5. 访问 API 文档
```
http://localhost:8080/doc.html
```

## 注意事项

1. **密码安全**: 所有密码都使用 MD5 加密存储
2. **状态管理**: 使用 StatusConstant 统一管理状态常量
3. **线程安全**: 使用 ThreadLocal 存储当前登录用户ID
4. **日志记录**: 关键操作都有详细的日志记录
5. **数据校验**: 前端和后端都需要进行数据校验

## 更新日志

### 2026-05-05
- ✨ 新增：菜品添加功能（支持口味管理）
- 🔧 实现：事务控制保证菜品和口味数据一致性
- 📝 完善：批量插入口味数据提高性能
- 🎯 应用：继续使用AOP自动填充审计字段

### 2026-05-04
- ✨ 新增：公共字段自动填充功能（AOP + 自定义注解 + 反射）
- 🔧 优化：移除 Service 层手动设置审计字段的重复代码
- 📝 完善：在 EmployeeMapper 和 CategoryMapper 中添加 @AutoFill 注解
- 🎯 实现：区分 INSERT 和 UPDATE 操作，填充不同的字段

### 2026-05-03
- ✨ 新增：启用/禁用员工账号功能
- ✨ 新增：编辑员工信息功能
- 🔒 优化：员工信息查询时密码脱敏处理
- 📝 完善：动态SQL更新，支持选择性字段更新

### 2026-05-01
- ✨ 新增：员工分页查询功能
- 📄 集成：PageHelper 分页插件

### 2026-04-30
- ✨ 新增：员工登录功能
- ✨ 新增：新增员工功能
- 🔐 集成：JWT 认证机制

---

**最后更新**: 2026-05-05
