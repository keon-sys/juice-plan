package com.juiceplan

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JuicePlanApplication

fun main(args: Array<String>) {
    runApplication<JuicePlanApplication>(*args)
}
