package com.cyril.llm.mcp.model;

// TODO 步骤3-1：定义 WeatherRequest POJO
// 导入：
//   org.springframework.ai.tool.annotation.ToolParam
//   lombok.Data  (或者自己写 getter/setter)
//
// 这是一个普通 Java Bean（POJO），不是 record！
// 因为要和 @Tool 配合使用，POJO 需要 getter 和 setter。
// 你可以用 Lombok @Data，或者手动生成 getter/setter。
//
// 字段：
//   String city  - @ToolParam(description = "城市")
//   String date  - @ToolParam(description = "日期")
//   再加两个故意含糊的字段名，看看大模型能不能理解：
//   String i    - @ToolParam(description = "区县")
//   String s    - @ToolParam(description = "街道")
//
// 为什么字段名要取 "i" 和 "s" 这么短？
//   这是故意的！为了验证 @ToolParam(description) 的作用。
//   如果没有 description，大模型看到 "i" 根本不知道是什么。
//   加上 description 后，大模型就能正确理解字段含义了。
//
// 提示：在 PDD 模块的 OrderChat，我们用了 record
//       这里必须用 class + getter/setter，因为框架需要通过
//       setter 或构造器来设置字段值

// TODO: 写 WeatherRequest 类
