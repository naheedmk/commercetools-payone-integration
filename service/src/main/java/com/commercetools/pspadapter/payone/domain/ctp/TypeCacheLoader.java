package com.commercetools.pspadapter.payone.domain.ctp;

import com.google.common.cache.CacheLoader;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.types.Type;
import io.sphere.sdk.types.queries.TypeQuery;

public class TypeCacheLoader extends CacheLoader<String, Type> {
    private final BlockingClient client;

    public TypeCacheLoader(BlockingClient client) {
        this.client = client;
    }

    @Override
    public Type load(String typeKey) throws Exception {
        final PagedQueryResult<Type> result = client.complete(
            TypeQuery.of()
                    .withPredicates(m -> m.key().is(typeKey))
                    .withLimit(1));
        return result.head().orElseThrow(() -> new IllegalStateException(typeKey + " was not found"));
    }
}