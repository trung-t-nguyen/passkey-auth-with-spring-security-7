package com.ttng.demo_web_auth.web

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {

    @GetMapping("/")
    fun home(authentication: Authentication, model: Model): String {
        model.addAttribute("username", authentication.name)
        return "home"
    }
}
