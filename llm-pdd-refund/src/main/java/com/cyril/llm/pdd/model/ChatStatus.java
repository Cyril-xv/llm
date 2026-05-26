package com.cyril.llm.pdd.model;

// TODO 步骤1-1：定义一个枚举，包含三个状态：CHAT_START（对话开始）、CHATTING（对话中）、CHAT_END（对话结束）
// 提示：这是一个标准的 Java 枚举，没有依赖任何框架，直接写就行
// 写完就编译验证： mvn -pl llm-pdd-refund compile
public enum ChatStatus {
    // TODO: 在这里写枚举值
    CHAT_START,
    CHATTING,
    CHAT_END
}
