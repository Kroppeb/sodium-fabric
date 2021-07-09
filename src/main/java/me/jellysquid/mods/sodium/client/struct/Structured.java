package me.jellysquid.mods.sodium.client.struct;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.PARAMETER})
@Documented
public @interface Structured {
    Structs value();
}
