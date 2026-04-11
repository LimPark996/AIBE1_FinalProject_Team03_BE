package com.team03.ticketmon.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 로그인 페이지 뷰 렌더링용 컨트롤러. 템플릿 기반 /auth/login 화면을 반환한다.
 */
@Controller
@RequestMapping("/auth")
public class loginController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }
}
