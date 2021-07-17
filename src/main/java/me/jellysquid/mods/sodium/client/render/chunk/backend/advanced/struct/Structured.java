package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.PARAMETER})
@Documented
public @interface Structured {
    Structs value();
}
