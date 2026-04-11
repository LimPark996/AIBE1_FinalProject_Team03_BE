package com.team03.ticketmon._global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HomeController — 루트 경로(/) 응답용 간단 컨트롤러
 *
 * 서비스 기동 여부를 확인하기 위한 최소한의 인덱스 응답을 반환합니다.
 */
@RestController
public class HomeController {
    @GetMapping("/")
    public String index() {
        return "서비스가 정상적으로 실행 중입니다!";
    }
}
