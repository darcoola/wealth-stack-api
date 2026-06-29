package com.wealthStack.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the bundled Angular single-page app from the static classpath and makes HTML5 client-side
 * routing work: any request that is not a real static file and is not an API call falls back to
 * `index.html`, so deep links like `/operations` (and a browser refresh on them) load the SPA.
 *
 * RestController mappings under the `api` path are matched before this resource handler, so the API
 * is unaffected; unknown API paths return 404 rather than the SPA shell.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    val requested = location.createRelative(resourcePath)
                    return when {
                        requested.exists() && requested.isReadable -> requested
                        resourcePath.startsWith("api/") -> null
                        else -> ClassPathResource("/static/index.html")
                    }
                }
            })
    }
}
