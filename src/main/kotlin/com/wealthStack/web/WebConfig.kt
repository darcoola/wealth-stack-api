package com.wealthStack.web

import com.wealthStack.security.PartyContextArgumentResolver
import org.springframework.boot.web.server.MimeMappings
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.method.support.HandlerMethodArgumentResolver
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
class WebConfig(
    private val partyContextArgumentResolver: PartyContextArgumentResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(partyContextArgumentResolver)
    }

    /**
     * Serve the PWA manifest with its correct media type. `.webmanifest` is absent from the servlet
     * container's default MIME map, so it would otherwise be sent as `application/octet-stream`;
     * `ResourceHttpRequestHandler` consults these servlet mappings first when choosing a content
     * type. Chrome/Android want `application/manifest+json` for a clean installable PWA.
     */
    @Bean
    fun webManifestMimeMapping(): WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            val mappings = MimeMappings(MimeMappings.DEFAULT)
            mappings.add("webmanifest", "application/manifest+json")
            factory.setMimeMappings(mappings)
        }

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
