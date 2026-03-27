package com.example.adoption.external;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExternalConsumerController {

    @GetMapping("/external")
    public String external() {
        return "ok";
    }
}
