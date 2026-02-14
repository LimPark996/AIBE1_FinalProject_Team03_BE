package com.team03.ticketmon._global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/")
    public String index() {
        return "서비스가 정상적으로 실행 중입니다!";
    }
}
