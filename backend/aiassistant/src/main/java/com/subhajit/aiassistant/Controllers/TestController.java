package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.Services.SpringAiTestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final SpringAiTestService service;

    public TestController(
            SpringAiTestService service
    ) {
        this.service = service;
    }

    @GetMapping("/test")
    public String test() {
        return service.test();
    }
}
