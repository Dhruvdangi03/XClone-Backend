package com.project.Xclone_backend.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Matches only when R2 credentials are present, so the app still boots locally without them. */
public class R2ConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return StringUtils.hasText(env.getProperty("app.r2.account-id"))
                && StringUtils.hasText(env.getProperty("app.r2.access-key"))
                && StringUtils.hasText(env.getProperty("app.r2.secret-key"));
    }
}
