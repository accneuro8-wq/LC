package fun.lumis.utils.client.managers.event;

import fun.lumis.utils.client.managers.event.types.Priority;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    byte value() default Priority.MEDIUM;
}
