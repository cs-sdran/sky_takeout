
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
java
// Service 层每个方法都要写
employee.setCreateTime(LocalDateTime.now());
employee.setUpdateTime(LocalDateTime.now());
employee.setCreateUser(BaseContext.getCurrentId());
employee.setUpdateUser(BaseContext.getCurrentId());
```

使用后（自动填充）:
java
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

#### 2.2 菜品分页查询 ⭐⭐ (5月6日完成)
- **接口路径**: `GET /admin/dish/page`
- **功能描述**: 按条件分页查询菜品列表
- **请求参数**: 
  - page: 页码
  - pageSize: 每页大小
  - name: 菜品名称（可选，支持模糊查询）
  - categoryId: 菜品分类ID（可选）
  - status: 菜品状态（可选，0-停售，1-起售）
- **返回数据**: 总记录数和菜品列表数据（包含分类名称）
- **技术实现**: 使用 PageHelper 实现物理分页，联表查询获取分类名称

**实现细节**:
java
// Controller层
@GetMapping("/page")
@ApiOperation("分页查询")
public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
    log.info("分页查询：{}",dishPageQueryDTO);
    PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
    return Result.success(pageResult);
}

// Service层
@Override
public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
    PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
    Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
    return new PageResult(page.getTotal(), page.getResult());
}

// Mapper层
Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

// XML映射文件
<select id="pageQuery" resultType="com.sky.vo.DishVO">
    select d.*,c.name as categoryName from dish d left outer join category c on d.category_id = c.id
    <where>
        <if test="name != null and name != ''">
            and d.name like CONCAT('%', #{name}, '%')
        </if>
        <if test="categoryId != null">
            and d.category_id = #{categoryId}
        </if>
        <if test="status != null">
            and d.status = #{status}
        </if>
    </where>
    order by d.update_time desc
</select>


**技术亮点**:
- ✨ **动态SQL**: 根据传入参数动态构建查询条件
- ✨ **联表查询**: 通过left join获取菜品对应的分类名称
- ✨ **模糊查询**: 支持按菜品名称进行模糊搜索
- ✨ **分页插件**: 使用PageHelper实现高效分页
- ✨ **视图对象**: 使用DishVO返回包含分类名称的完整信息

#### 2.3 菜品删除 ⭐⭐ (5月6日完成)
- **接口路径**: `DELETE /admin/dish`
- **功能描述**: 批量删除菜品及其关联的口味数据
- **请求参数**: ids: 菜品ID列表
- **业务规则**:
  - 起售中的菜品不能删除
  - 被套餐关联的菜品不能删除
  - 删除菜品时同步删除关联的口味数据
  - 使用事务保证数据一致性

**实现细节**:
java

// Controller层
@DeleteMapping
@ApiOperation("批量删除菜品")
public Result delete(@RequestParam List<Long> ids) {
    log.info("批量删除菜品：{}",ids);
    dishService.deleteBatch(ids);
    return Result.success();
}

// Service层
@Override
@Transactional
public void deleteBatch(List<Long> ids) {
    // 1. 判断菜品状态
    for (Long id : ids) {
        Dish dish = dishMapper.getById(id);
        if (dish.getStatus() == StatusConstant.ENABLE) {
            // 起售中的菜品不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        }
    }

    // 2. 判断是否被套餐关联
    List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
    if (setmealIds != null && setmealIds.size() > 0) {
        // 当前菜品有被套餐关联，不能删除
        throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
    }

    // 3. 删除菜品
    dishMapper.deleteByIds(ids);

    // 4. 删除菜品关联的口味数据
    dishFlavorMapper.deleteByDishIds(ids);
}

// Mapper层
void deleteByIds(List<Long> ids);

// XML映射文件
  <delete id="deleteByIds">
      delete from dish where id in
      <foreach item="id" collection="ids" open="(" separator="," close=")">
          #{id}
      </foreach>
  </delete>
```

**技术亮点**:
- ✨ **事务管理**: 使用  确保菜品和口味数据同时删除
- ✨ **业务校验**: 删除前检查菜品状态和套餐关联关系
- ✨ **批量删除**: 使用MyBatis foreach实现批量删除
- ✨ **异常处理**: 对不允许删除的情况抛出业务异常
- ✨ **数据完整性**: 保证主表和关联表数据的一致性

#### 2.4 修改菜品 ⭐⭐ (5月8日完成)
- **接口路径**: `PUT /admin/dish`
- **功能描述**: 管理员修改菜品基本信息和口味数据
- **涉及表**: 
  - `dish`: 菜品基本信息表
  - `dish_flavor`: 菜品口味关系表
- **请求参数**:
  - id: 菜品ID
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
  - 先更新菜品基本信息，再删除原有口味数据，最后插入新的口味数据
  - 自动填充更新时间、更新人等审计字段

**实现细节**:
```java
// Controller层
@PutMapping
@ApiOperation("修改菜品")
public Result update(@RequestBody DishDTO dishDTO) {
    log.info("修改菜品：{}",dishDTO);
    dishService.updateWithFlavor(dishDTO);
    return Result.success();
}

// Service层
@Override
@Transactional
public void updateWithFlavor(DishDTO dishDTO) {
    // 1. 将DTO转换为Entity并更新菜品基本信息
    Dish dish = new Dish();
    BeanUtils.copyProperties(dishDTO, dish);
    dishMapper.update(dish);

    // 2. 删除原有的口味数据
    dishFlavorMapper.deleteByDishId(dishDTO.getId());

    // 3. 处理新的口味数据
    List<DishFlavor> flavors = dishDTO.getFlavors();
    if (flavors != null && flavors.size() > 0) {
        // 设置每个口味的菜品ID
        for (DishFlavor flavor : flavors) {
            flavor.setDishId(dishDTO.getId());
        }
        // 批量插入新的口味数据
        dishFlavorMapper.insertBatch(flavors);
    }
}

// Mapper层
@AutoFill(value = OperationType.UPDATE)
void update(Dish dish);
```

**技术亮点**:
- ✨ **事务管理**: 使用@Transactional注解确保菜品和口味数据的一致性
- ✨ **先删后增**: 采用先删除原有口味再插入新口味的方式简化逻辑
- ✨ **批量操作**: 口味数据采用批量插入提高性能
- ✨ **自动填充**: 利用AOP自动填充审计字段，减少重复代码
- ✨ **数据传输对象**: 使用DishDTO接收前端复杂数据结构
- ✨ **属性拷贝**: 使用BeanUtils简化DTO到Entity的转换

### 3. 套餐管理模块

#### 3.1 新增套餐 ⭐⭐ (5月17日完成)
- **接口路径**: `POST /admin/setmeal`
- **功能描述**: 管理员新增套餐信息，包括基本信息和关联的菜品数据
- **涉及表**: 
  - `setmeal`: 套餐基本信息表
  - `setmeal_dish`: 套餐菜品关系表
- **请求参数**:
  - name: 套餐名称
  - categoryId: 套餐分类ID
  - price: 套餐价格
  - image: 套餐图片URL
  - description: 套餐描述
  - status: 套餐状态（0-停售，1-起售）
  - setmealDishes: 套餐菜品列表
    - dishId: 菜品ID
    - name: 菜品名称
    - price: 菜品单价
    - copies: 份数
- **业务规则**:
  - 使用事务保证数据一致性
  - 先插入套餐基本信息，再批量插入关联的菜品数据
  - 自动填充创建时间、更新时间、创建人、更新人等审计字段

**实现细节**:
```java
// Controller层
@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐管理接口")
@Slf4j
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    
    /**
     * 新增套餐
     * @return
     */
    @PostMapping
    @ApiOperation("新增套餐")
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}",setmealDTO);
        setmealService.saveWithDish(setmealDTO);
        return Result.success();
    }
}

// Service层
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        // 1. 将DTO转换为Entity并插入套餐基本信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);

        // 2. 获取生成的套餐ID
        Long setmealId = setmeal.getId();
        
        // 3. 处理套餐菜品数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && setmealDishes.size() > 0) {
            // 设置每个菜品的套餐ID
            for (SetmealDish setmealDish : setmealDishes) {
                setmealDish.setSetmealId(setmealId);
            }
            // 批量插入套餐菜品数据
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }
}

// Mapper层
@Mapper
public interface SetmealMapper {
    /**
     * 插入套餐数据
     * @param setmeal
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Setmeal setmeal);
}

@Mapper
public interface SetmealDishMapper {
    /**
     * 批量插入套餐菜品数据
     * @param setmealDishes
     */
    @AutoFill(value = OperationType.INSERT)
    void insertBatch(List<SetmealDish> setmealDishes);
}
```

**技术亮点**:
- ✨ **事务管理**: 使用@Transactional注解确保套餐和菜品数据的一致性
- ✨ **批量操作**: 套餐菜品数据采用批量插入提高性能
- ✨ **自动填充**: 利用AOP自动填充审计字段，减少重复代码
- ✨ **数据传输对象**: 使用SetmealDTO接收前端复杂数据结构
- ✨ **属性拷贝**: 使用BeanUtils简化DTO到Entity的转换

#### 3.2 套餐分页查询 ⭐⭐ (5月17日完成)
- **接口路径**: `GET /admin/setmeal/page`
- **功能描述**: 按条件分页查询套餐列表
- **请求参数**: 
  - page: 页码
  - pageSize: 每页大小
  - name: 套餐名称（可选，支持模糊查询）
  - categoryId: 套餐分类ID（可选）
  - status: 套餐状态（可选，0-停售，1-起售）
- **返回数据**: 总记录数和套餐列表数据（包含分类名称）
- **技术实现**: 使用 PageHelper 实现物理分页，联表查询获取分类名称

**实现细节**:
```java
// Controller层
@GetMapping("/page")
@ApiOperation("分页查询")
public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
    log.info("分页查询：{}",setmealPageQueryDTO);
    PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
    return Result.success(pageResult);
}

// Service层
@Override
public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
    PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
    Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
    return new PageResult(page.getTotal(), page.getResult());
}

// Mapper层
Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

// XML映射文件
<select id="pageQuery" resultType="com.sky.vo.SetmealVO">
    select s.*,c.name as categoryName from setmeal s left outer join category c on s.category_id = c.id
    <where>
        <if test="name != null and name != ''">
            and s.name like CONCAT('%', #{name}, '%')
        </if>
        <if test="categoryId != null">
            and s.category_id = #{categoryId}
        </if>
        <if test="status != null">
            and s.status = #{status}
        </if>
    </where>
    order by s.update_time desc
</select>
```

**技术亮点**:
- ✨ **动态SQL**: 根据传入参数动态构建查询条件
- ✨ **联表查询**: 通过left join获取套餐对应的分类名称
- ✨ **模糊查询**: 支持按套餐名称进行模糊搜索
- ✨ **分页插件**: 使用PageHelper实现高效分页
- ✨ **视图对象**: 使用SetmealVO返回包含分类名称的完整信息

#### 3.3 套餐删除 ⭐⭐ (5月17日完成)
- **接口路径**: `DELETE /admin/setmeal`
- **功能描述**: 批量删除套餐及其关联的菜品数据
- **请求参数**: ids: 套餐ID列表
- **业务规则**:
  - 起售中的套餐不能删除
  - 删除套餐时同步删除关联的菜品数据
  - 使用事务保证数据一致性

**实现细节**:
```java
// Controller层
@DeleteMapping
@ApiOperation("批量删除套餐")
public Result delete(@RequestParam List<Long> ids) {
    log.info("批量删除套餐：{}",ids);
    setmealService.deleteBatch(ids);
    return Result.success();
}

// Service层
@Override
@Transactional
public void deleteBatch(List<Long> ids) {
    // 1. 判断套餐状态
    for (Long id : ids) {
        Setmeal setmeal = setmealMapper.getById(id);
        if (setmeal.getStatus() == StatusConstant.ENABLE) {
            // 起售中的套餐不能删除
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }
    }

    // 2. 删除套餐
    setmealMapper.deleteByIds(ids);

    // 3. 删除套餐关联的菜品数据
    setmealDishMapper.deleteBySetmealIds(ids);
}

// Mapper层
void deleteByIds(List<Long> ids);

// XML映射文件
<delete id="deleteByIds">
    delete from setmeal where id in
    <foreach item="id" collection="ids" open="(" separator="," close=")">
        #{id}
    </foreach>
</delete>
```

**技术亮点**:
- ✨ **事务管理**: 使用@Transactional确保套餐和菜品数据同时删除
- ✨ **业务校验**: 删除前检查套餐状态
- ✨ **批量删除**: 使用MyBatis foreach实现批量删除
- ✨ **异常处理**: 对不允许删除的情况抛出业务异常
- ✨ **数据完整性**: 保证主表和关联表数据的一致性

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
- ✅ 5月6日: 菜品分页查询功能
- ✅ 5月6日: 菜品删除功能
- ✅ 5月8日: 菜品修改功能（含口味管理）
- ✅ 5月17日: 套餐新增功能（含菜品管理）
- ✅ 5月17日: 套餐分页查询功能
- ✅ 5月17日: 套餐删除功能
- ✅ 5月18日: 套餐修改功能（含菜品管理）

#### 3.4 套餐修改 ⭐⭐ (5月18日完成)
- **接口路径**: `PUT /admin/setmeal`
- **功能描述**: 管理员修改套餐基本信息和关联的菜品数据
- **涉及表**: 
  - `setmeal`: 套餐基本信息表
  - `setmeal_dish`: 套餐菜品关系表
- **请求参数**:
  - id: 套餐ID
  - name: 套餐名称
  - categoryId: 套餐分类ID
  - price: 套餐价格
  - image: 套餐图片URL
  - description: 套餐描述
  - status: 套餐状态（0-停售，1-起售）
  - setmealDishes: 套餐菜品列表
    - dishId: 菜品ID
    - name: 菜品名称
    - price: 菜品单价
    - copies: 份数
- **业务规则**:
  - 使用事务保证数据一致性
  - 先更新套餐基本信息，再删除原有菜品关联数据，最后插入新的菜品关联数据
  - 自动填充更新时间、更新人等审计字段

**实现细节**:
```java
// Controller层
@PutMapping
@ApiOperation("修改套餐")
public Result update(@RequestBody SetmealDTO setmealDTO) {
    log.info("更新套餐");
    setmealService.update(setmealDTO);
    return Result.success();
}

// Service层
@Override
@Transactional
public void update(SetmealDTO setmealDTO) {
    Setmeal setmeal = new Setmeal();
    BeanUtils.copyProperties(setmealDTO, setmeal);

    //1、修改套餐表，执行update
    setmealMapper.update(setmeal);


    //套餐id
    Long setmealId = setmealDTO.getId();

    //2、删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
    setmealDishMapper.deleteBySetmealId(setmealId);

    List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
    setmealDishes.forEach(setmealDish -> {
        setmealDish.setSetmealId(setmealId);
    });
    //3、重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
    setmealDishMapper.insertBatch(setmealDishes);
}

// Mapper层
@AutoFill(value = OperationType.UPDATE)
void update(Setmeal setmeal);
```

**技术亮点**:
- ✨ **事务管理**: 使用@Transactional注解确保套餐和菜品数据的一致性
- ✨ **先删后增**: 采用先删除原有菜品关联再插入新关联的方式简化逻辑
- ✨ **批量操作**: 套餐菜品数据采用批量插入提高性能
- ✨ **自动填充**: 利用AOP自动填充审计字段，减少重复代码
- ✨ **数据传输对象**: 使用SetmealDTO接收前端复杂数据结构
- ✨ **属性拷贝**: 使用BeanUtils简化DTO到Entity的转换

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

### 2026-05-18
- ✨ 新增：套餐修改功能（支持菜品管理）
- 🔧 实现：先删后增策略处理套餐菜品关联变更
- 📝 完善：事务控制保证套餐和菜品数据一致性
- 🎯 应用：继续使用AOP自动填充审计字段

### 2026-05-17
- ✨ 新增：套餐新增功能（支持菜品管理）
- ✨ 新增：套餐分页查询功能（支持多条件筛选）
- ✨ 新增：套餐删除功能（支持批量删除）
- 🔧 实现：事务控制保证套餐和菜品数据一致性
- 📝 完善：批量插入套餐菜品数据提高性能
- 🎯 应用：继续使用AOP自动填充审计字段

### 2026-05-08
- ✨ 新增：菜品修改功能（支持口味数据更新）
- 🔧 实现：先删后增策略处理口味数据变更
- 📝 完善：事务控制保证菜品和口味数据一致性
- 🎯 应用：继续使用AOP自动填充审计字段

### 2026-05-06
- ✨ 新增：菜品分页查询功能（支持多条件筛选）
- ✨ 新增：菜品删除功能（支持批量删除）
- 🔧 实现：动态SQL构建查询条件，支持模糊搜索
- 📝 完善：联表查询获取分类名称，提升用户体验
- 🔒 优化：删除前进行业务校验，确保数据完整性
- 🎯 应用：继续使用事务管理保证数据一致性

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

**最后更新**: 2026-05-18
