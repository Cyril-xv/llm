package com.cyril.llm.mcp.model;

// TODO 步骤3-2：定义 WeatherResponse POJO
// 这是 queryWeather 工具的返回值类型，框架会自动序列化。
// 同样需要 getter/setter 或者构造器。
//
// 字段：
//   String city        - 城市
//   String date        - 日期
//   String weather     - 天气描述（如"晴朗，有微风"）
//   double temperature - 温度
//
// 两种写法选一种：
//   1. 用 Lombok @Data
//   2. 手写 private 字段 + getter/setter + 全参构造器
//
// 建议用手写，练习时少一个依赖更清晰

// TODO: 写 WeatherResponse 类
