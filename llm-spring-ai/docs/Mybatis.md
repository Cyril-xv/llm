# MyBatis 入门与实践指南

## 一、什么是 MyBatis

MyBatis 是一款优秀的半自动 ORM 框架，它封装了 JDBC 的样板代码，让开发者把精力集中在 SQL 本身。与全自动 ORM（如 Hibernate、Spring Data JPA）不同，MyBatis 不会替你自动生成 SQL，SQL 由你手写，框架只负责参数绑定、结果集映射和连接管理。这种方式给了开发者对 SQL 的完全控制权，便于针对复杂查询做 SQL 优化。

MyBatis 的核心思想是：SQL 写在 XML 文件或注解中，通过 Mapper 接口将 SQL 与 Java 方法一一对应。运行时，MyBatis 根据方法签名找到对应的 SQL 语句，完成参数传入和结果集到实体对象的映射。

## 二、MyBatis 的核心组件

SqlSessionFactoryBuilder 用于创建 SqlSessionFactory，它读取 mybatis-config.xml 全局配置文件构建工厂实例。SqlSessionFactory 是线程安全的，整个应用只需要一个实例，通常通过单例或 Spring 容器管理。SqlSession 是执行 SQL 的会话对象，它不是线程安全的，每个线程使用完必须关闭，Spring 整合后由框架负责创建和关闭。Executor 是执行器，负责 SQL 的执行、缓存维护和事务控制，分为简单执行器、可重用执行器和批量执行器三种。

Mapper 接口是 MyBatis 中最核心的组件，一个接口对应一组 SQL 操作。接口本身没有实现类，MyBatis 通过 JDK 动态代理在运行时生成代理对象，调用接口方法时执行对应的 SQL。在 Spring 项目中，通过 @Mapper 注解或 @MapperScan 扫描包路径，就可以把 Mapper 接口注册为 Spring Bean 并自动注入使用。

## 三、#{} 与 ${} 的区别

#{} 是预编译参数占位符，MyBatis 会将其编译成 JDBC 的 ? 占位符，由 PreparedStatement 进行参数绑定，能够有效防止 SQL 注入攻击，所有需要传值的场景都应该优先使用它。${} 是字符串拼接，MyBatis 直接把参数值原样拼接到 SQL 字符串中，不经过预编译，存在 SQL 注入风险。

${} 只能在少数特定场景使用，比如动态指定表名、列名或排序字段，因为这些是 SQL 结构的一部分，无法用占位符表示。使用 ${} 时必须确保传入值来自可信来源，比如代码中的常量枚举，绝不能直接拼接用户输入。

## 四、MyBatis 一级缓存与二级缓存

一级缓存是 SqlSession 级别的缓存，默认开启。同一个 SqlSession 中执行两次相同的查询，第二次会直接命中缓存而不查数据库。一级缓存的生效条件是：两次查询的 SQL 和参数完全一致，且两次查询之间没有执行增删改操作，因为增删改会使缓存失效。SqlSession 关闭或 commit 后，一级缓存也会被清空。

二级缓存是 Mapper 级别的缓存，跨 SqlSession 共享，默认关闭需要手动开启。在 XML 映射文件中配置 cache 标签即可开启，它基于 namespace 维度缓存，一个 namespace 下所有 SqlSession 的查询结果共享。二级缓存要求实体类实现序列化接口，且所有增删改操作都会清空对应 namespace 的缓存。二级缓存适合读多写少、数据一致性要求不高的场景，并发修改频繁的业务要谨慎使用。

## 五、动态 SQL

动态 SQL 是 MyBatis 最强大的特性之一，它允许在 XML 中根据条件拼接 SQL。if 标签做简单的条件判断，满足条件才拼入对应片段。where 标签会自动处理前导的 AND 或 OR 关键字，避免 SQL 语法错误。set 标签用于更新语句，自动处理尾随逗号。choose 标签类似 Java 的 switch，when 和 otherwise 配合使用，只执行第一个满足的分支。foreach 标签用于遍历集合，实现 in 查询或批量插入。

动态 SQL 的最佳实践是：能用一条 SQL 解决的就不要拆成多条，但过于复杂的动态拼接会让 SQL 难以维护，必要时可以拆分为多个 Mapper 方法。

## 六、结果映射 resultMap

resultMap 用于处理数据库列名和 Java 属性名不一致的情况，也可以处理一对多、多对一关联查询。通过 resultMap 的 association 标签配置一对一关联，collection 标签配置一对多集合。开启驼峰映射后，数据库的 user_name 列可以自动映射到 userName 属性，无需逐个配置。

resultType 和 resultMap 的选择：简单场景用 resultType 直接指定实体类型，复杂映射场景用 resultMap 明确声明列与属性的对应关系。

## 七、MyBatis 与 Spring Boot 整合

在 Spring Boot 中使用 MyBatis 非常简单：引入 mybatis-spring-boot-starter 依赖，在配置文件中指定 mybatis.mapper-locations 指向 XML 映射文件位置，通过 @MapperScan 指定 Mapper 接口扫描包。Spring Boot 自动配置会创建 SqlSessionFactory 和 SqlSessionTemplate 注入容器，开发者只需要注入 Mapper 接口即可使用。

## 八、注解开发与 XML 开发的选择

MyBatis 支持注解方式编写 SQL，比如 @Select、@Insert、@Update、@Delete 注解直接写在接口方法上，适合简单 SQL。注解方式无法表达动态 SQL 的全部能力，复杂的 SQL 还是需要 XML。实际项目中推荐混合使用：简单查询用注解，复杂查询和动态 SQL 用 XML，保持 Mapper 接口与 XML 文件同名同包，便于维护。

## 九、插件机制与拦截器

MyBatis 的插件机制基于 JDK 动态代理，可以在 Executor、StatementHandler、ParameterHandler、ResultSetHandler 四个核心对象上拦截调用。通过 @Intercepts 注解声明拦截的方法签名，实现 Interceptor 接口即可编写插件。常见的插件用途包括：SQL 日志打印、慢 SQL 统计、分页插件（如 PageHelper 的实现原理）、数据权限拦截。

## 十、MyBatis 与 JPA 的对比

MyBatis 是半自动 ORM，SQL 由开发者掌控，适合 SQL 复杂、对性能要求高的系统，国内互联网公司使用广泛。JPA 是全自动 ORM，开发者面向对象编程，框架自动生成 SQL，开发效率高，适合表结构简单、标准 CRUD 为主的业务系统。选择依据主要是团队对 SQL 的掌控需求和业务复杂度。
