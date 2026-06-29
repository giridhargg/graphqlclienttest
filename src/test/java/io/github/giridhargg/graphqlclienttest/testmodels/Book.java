package io.github.giridhargg.graphqlclienttest.testmodels;

import org.jspecify.annotations.Nullable;

public record Book(@Nullable String id, @Nullable String name, @Nullable Author author) {}
