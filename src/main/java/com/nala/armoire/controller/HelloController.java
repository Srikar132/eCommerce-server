package com.nala.armoire.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HelloController {
    @GetMapping("/hello")
    public String greet() {
        System.out.println("🔥 Hello endpoint executed");
        return "Hello World mahi";
    }
}

