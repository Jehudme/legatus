package dev.legatus.server;

import dev.legatus.server.config.AsyncSyncConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = { LegatusServerApp.class, AsyncSyncConfiguration.class, dev.legatus.server.config.JacksonHibernateConfiguration.class }
)
public @interface IntegrationTest {}
