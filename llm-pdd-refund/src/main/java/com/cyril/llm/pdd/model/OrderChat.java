package com.cyril.llm.pdd.model;

// TODO 步骤1-2：定义一个 record，用于接收 LLM 的结构化输出
// 需要导入：com.fasterxml.jackson.annotation.JsonPropertyDescription
//
// 这个 record 的作用：
//   当调用 /newChat 接口时，LLM 会按照这个结构返回 JSON，
//   Spring AI 自动反序列化为 OrderChat 对象，前端就能拿到结构化的对话信息
//
// 字段要求（共4个）：
//   String orderId   - @JsonPropertyDescription("订单号")
//   String userId    - @JsonPropertyDescription("用户Id")
//   String chatId    - @JsonPropertyDescription("对话Id")
//   ChatStatus status - @JsonPropertyDescription("对话状态")
//
// 提示：
//   1. 用 Java record 而非 class，更简洁
//   2. @JsonPropertyDescription 是 Jackson 的注解，告诉 LLM 每个字段的含义
//   3. record 的构造函数参数顺序就是 JSON 字段顺序

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// TODO: 在这里定义 record
public record OrderChat(
        @JsonPropertyDescription("订单号") String orderId,
        @JsonPropertyDescription("用户Id") String userId,
        @JsonPropertyDescription("对话Id") String chatId,
        @JsonPropertyDescription("对话状态") ChatStatus statusÒ
) {
}
