package com.raft.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RaftController {

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @PostMapping("/start-election")
    public String startElection() {
        // Implement election logic here
        return "redirect:/";
    }

    @PostMapping("/add-log")
    public String addLogEntry(@RequestParam String entry) {
        // Implement log entry logic here
        return "redirect:/";
    }
} 