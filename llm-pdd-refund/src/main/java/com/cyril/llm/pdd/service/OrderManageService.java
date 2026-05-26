package com.cyril.llm.pdd.service;

// TODO 步骤2：模拟一个已存在的订单退款服务
// 需要导入：org.springframework.stereotype.Service, java.util.UUID
//
// 这个 Service 的作用：
//   模拟项目中已有的退款逻辑，被后面的 OrderTools 调用。
//   真实项目中这里会连数据库、调支付网关，这里我们只做打印和返回。
//
// 需要实现两个方法：
//   1. public String getOrderById(String orderId)
//      - 返回 "订单号：" + orderId
//      - 模拟查询订单信息
//
//   2. public String refund(String orderId, String reason)
//      - System.out.println("退款成功，订单号: " + orderId + "，原因: " + reason)
//      - return UUID.randomUUID().toString()  // 返回退款单号
//      - 模拟真实退款流程
//
// 提示：加上 @Service 注解让 Spring 管理

// TODO: 在这里写 Service 类
