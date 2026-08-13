package com.openex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class OpenExApplication

fun main(args: Array<String>) {
    runApplication<OpenExApplication>(*args)
}
