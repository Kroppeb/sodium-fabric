package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE})
@Documented
public @interface Structured {
    Structs value();
}
