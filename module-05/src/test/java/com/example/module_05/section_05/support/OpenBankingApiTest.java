package com.example.module_05.section_05.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** A composed annotation: the extension plus a tag, so test classes need one line of set-up. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenBankingApiExtension.class)
@Tag("api")
public @interface OpenBankingApiTest {
}
