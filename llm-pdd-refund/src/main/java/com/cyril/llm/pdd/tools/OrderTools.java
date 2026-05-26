package com.cyril.llm.pdd.tools;

import com.cyril.llm.pdd.service.OrderManageService;

// TODO 步骤3：定义 Function Calling 工具（★★★ 核心知识点 ★★★）
// 需要导入：
//   org.springframework.ai.tool.annotation.Tool
//   org.springframework.ai.tool.annotation.ToolParam
//   org.springframework.stereotype.Component
//
// 这个类的核心作用：
//   通过 @Tool 注解，让 LLM 知道自己可以调用"退款"这个能力。
//   当 LLM 判断用户反馈的是质量问题时，它会自动调用 apply_refund 方法，
//   传入对话历史中记住的 orderId、商品名、退款原因。
//
// 关键注解说明：
//   @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
//     - name: LLM 看到的工具名称
//     - description: 告诉 LLM 这个工具做什么，什么时候该调用
//
//   @ToolParam(description = "订单编号，为数字类型")
//     - description: 告诉 LLM 这个参数应该填什么，帮助 LLM 从对话中提取正确的值
//
// 工作流程：
//   1. 用户在对话中说"衣服质量太差了，袖口开线了"
//   2. LLM 根据 system prompt 判断这是质量问题
//   3. LLM 决定调用 apply_refund，从记忆中找到 orderId
//   4. Spring AI 自动执行这个方法，拿到返回值
//   5. LLM 把返回值包装成自然语言回复用户
//
// 需要注入的依赖：
//   private final OrderManageService orderManageService;  // 构造函数注入
//
// 需要实现的方法：
//   @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
//   public String refund(
//       @ToolParam(description = "订单编号，为数字类型") String orderId,
//       @ToolParam(description = "商品名称") String name,
//       @ToolParam(description = "退款原因") String reason
//   ) {
//       // 1. 打印日志：System.out.println(...)
//       // 2. 调用 orderManageService.refund(orderId, reason)
//       // 3. 返回确认信息给 LLM
//   }

// TODO: 在这里写 OrderTools 类
