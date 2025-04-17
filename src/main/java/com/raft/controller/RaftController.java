package com.raft.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RaftController {
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
} 