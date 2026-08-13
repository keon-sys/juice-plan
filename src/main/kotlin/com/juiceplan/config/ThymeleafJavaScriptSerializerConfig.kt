package com.juiceplan.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.dialect.SpringStandardDialect
import org.thymeleaf.standard.serializer.IStandardJavaScriptSerializer

/**
 * Thymeleaf's default `th:inline="javascript"` serializer (Jackson-backed) hard-codes
 * ESCAPE_NON_ASCII=true, so Korean text embedded via JS inlining (e.g. DAY_NOTES memos)
 * would render as `\uXXXX` escapes instead of readable UTF-8 characters. This is valid
 * JS but inconsistent with the rest of the (UTF-8) page and hostile to debugging.
 *
 * Swap in an equivalent Jackson serializer without the ASCII-escaping restriction.
 */
@Component
class ThymeleafJavaScriptSerializerConfig(private val templateEngine: SpringTemplateEngine) {

    @PostConstruct
    fun useUnicodeSafeJavaScriptSerializer() {
        val mapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        }
        val serializer = IStandardJavaScriptSerializer { value, writer -> mapper.writeValue(writer, value) }
        templateEngine.dialects
            .filterIsInstance<SpringStandardDialect>()
            .forEach { it.javaScriptSerializer = serializer }
    }
}
