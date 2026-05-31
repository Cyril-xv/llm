package com.cyril.llm.mcp.service;

// TODO 步骤2：编写 MCP 工具（★★★★ 核心知识点 ★★★★）
// 导入：
//   org.springframework.ai.tool.annotation.Tool
//   org.springframework.ai.tool.annotation.ToolParam
//   com.cyril.llm.mcp.model.WeatherRequest
//   com.cyril.llm.mcp.model.WeatherResponse
//   org.springframework.stereotype.Service
//
// ═══════════════════════════════════════════════════
// 知识点：MCP 的工具定义方式和 Spring AI 完全一致
// ═══════════════════════════════════════════════════
//
// MCP (Model Context Protocol) 的 Tool 定义就是 @Tool 注解，
// 和你在 PDD 模块里写过的 OrderTools 完全一样！
// 区别在于 MCP Server 不直接面向用户聊天，
// 而是把工具注册到 MCP 协议中，让 AI 客户端（如 Cline）调用。
//
// 本节需要实现两个方法：
//
// 1. 简单工具：getWeather(String city)
//    @Tool(description = "根据城市名称查询天气信息")
//    public String getWeather(String city)
//
//    返回示例：
//      "北京" → "北京: 晴, 25°C"
//      "上海" → "上海: 多云, 22°C"
//      其他   → city + ": 下雪, -20°C"
//
//    提示：用 switch 表达式实现，判断 city 参数
//
// 2. 复杂工具：queryWeather(WeatherRequest request)  ★★★
//    @Tool(name = "query_weather_by_city_date",
//          description = "根据城市和日期获取天气信息")
//    public WeatherResponse queryWeather(WeatherRequest request)
//
//    这个方法的入参和出参都是 POJO 类（普通的 Java 对象），
//    不是基本类型！这是 MCP / Function Calling 的高级用法。
//
//    实现：
//      模拟调用外部 API，Thread.sleep(5000) 模拟耗时
//      double temp = Math.random() * 15 + 10;
//      return new WeatherResponse(
//          request.getCity(),
//          request.getDate(),
//          "晴朗，有微风",
//          temp
//      );
//
//    注意：@Tool 方法使用 POJO 作为参数时，框架会自动展开
//    为 tool schema 中的多个字段，大模型会根据字段名和
//    @ToolParam(description) 来理解每个字段的含义

// TODO: 写 WeatherService 类（需要 @Service）+ 两个 @Tool 方法

import com.cyril.llm.mcp.model.WeatherRequest;
import com.cyril.llm.mcp.model.WeatherResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    @Tool(description = "根据城市名称查询天气信息")
    public String getWeather(String city) {
        if (city == null || city.isEmpty()) {
            return "请提供城市名称";
        }
        return switch (city) {
            case "北京" -> "北京: 晴, 25°C";
            case "上海" -> "上海: 多云, 22°C";
            case "深圳" -> "深圳: 小雨, 28°C";
            default -> city + ": 下雪, -20°C";
        };
    }

    /*
     * POJO 入参 + POJO 出参的 @Tool 方法
     *
     * WeatherRequest 使用了 Lombok @Data 注解，
     * 框架会自动通过 setter 填充字段值。
     *
     * WeatherResponse 需要全参构造器或 setter 用来反序列化。
     */
    @Tool(name = "query_weather_by_city_date", description = "根据城市和日期获取天气信息")
    public WeatherResponse queryWeather(WeatherRequest request) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        double temp = Math.random() * 15 + 10;

        return new WeatherResponse(
                request.getCity(),
                request.getDate(),
                "晴朗,有微风",
                temp
        );
    }
}
