//package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;
//
//import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Import;
//import org.springframework.core.annotation.AliasFor;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//
//import java.lang.annotation.Documented;
//import java.lang.annotation.ElementType;
//import java.lang.annotation.Inherited;
//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
//import java.lang.annotation.Target;
//
//@Target(ElementType.TYPE)
//@Retention(RetentionPolicy.RUNTIME)
//@Documented
//@Inherited
//@ExtendWith(SpringExtension.class)
//@Import({HttpSyncGraphQlClientTestAutoConfiguration.class})
//@EnableConfigurationProperties(GraphQlStaticTestAssetsProperties.class)
//public @interface HttpSyncGraphQlClientTest {
//
//    @AliasFor(annotation = Import.class, attribute = "value")
//    Class<?>[] classes() default {};
//}
