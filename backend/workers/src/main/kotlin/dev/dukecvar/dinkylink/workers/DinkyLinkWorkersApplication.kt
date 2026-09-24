package dev.dukecvar.dinkylink.workers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DinkyLinkWorkersApplication

fun main(args: Array<String>) {
	runApplication<DinkyLinkWorkersApplication>(*args)
}
