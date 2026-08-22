package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.rag.QuestionRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 问题重写器调试接口：单独验证每个重写方法的效果
 */
@Slf4j
@RestController
@RequestMapping("/rewrite")
public class RewriteController {

    private final QuestionRewriteService rewriteService;

    @Autowired
    public RewriteController(QuestionRewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    @GetMapping("/decompose")
    public List<String> decompose(@RequestParam("query") String query) {
        return rewriteService.decompose(query);
    }

    @GetMapping("/enrich")
    public String enrich(@RequestParam("chatHistory") String chatHistory,
                         @RequestParam("query") String query) {
        return rewriteService.enrich(chatHistory, query);
    }
}
