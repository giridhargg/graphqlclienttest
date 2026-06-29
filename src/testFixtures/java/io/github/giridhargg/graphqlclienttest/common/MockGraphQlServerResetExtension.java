package io.github.giridhargg.graphqlclienttest.common;

import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit 5 extension that resets every {@link MockGraphQlServer} bean in the test's Spring context
 * before each test method, so stubbed responses never bleed from one test method into the next —
 * even when the Spring context (and therefore the {@code MockGraphQlServer} bean instance) is
 * shared/cached across test methods or test classes, as it normally is without
 * {@code @DirtiesContext}.
 *
 * <p>Registered automatically by both {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * and {@link
 * io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration}
 * — consumers do not need to reference this class directly.
 */
public class MockGraphQlServerResetExtension implements BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    var appContext = SpringExtension.getApplicationContext(context);
    appContext.getBeansOfType(MockGraphQlServer.class).values().forEach(MockGraphQlServer::reset);
    appContext.getBeansOfType(ApqRegistry.class).values().forEach(ApqRegistry::reset);
  }
}
