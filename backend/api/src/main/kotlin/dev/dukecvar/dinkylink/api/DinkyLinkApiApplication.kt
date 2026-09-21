package dev.dukecvar.dinkylink.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DinkyLinkApiApplication

fun main(args: Array<String>) {
	runApplication<DinkyLinkApiApplication>(*args)
}
