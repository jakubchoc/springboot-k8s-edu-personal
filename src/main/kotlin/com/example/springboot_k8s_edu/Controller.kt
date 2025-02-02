package com.example.springboot_k8s_edu

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class Controller {

    @GetMapping
    fun hello(): String {
        return "Hello from Kube!"
    }
}