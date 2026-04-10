/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.protobufs.ObjectMap;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.transport.grpc.proto.response.common.ObjectMapProtoUtils;

import java.util.function.Consumer;

/**
 * Shared utility methods for terms aggregation proto converters.
 */
class TermsAggregateProtoUtils {

    private TermsAggregateProtoUtils() {}

    /**
     * Applies metadata from an InternalAggregation to a typed terms aggregate builder
     * via the provided setter function.
     *
     * @param metaSetter the setMeta method reference on the typed builder
     * @param aggregation the source aggregation
     */
    static void applyMetadata(Consumer<ObjectMap> metaSetter, InternalAggregation aggregation) {
        if (aggregation.getMetadata() != null && !aggregation.getMetadata().isEmpty()) {
            ObjectMap.Value metaValue = ObjectMapProtoUtils.toProto(aggregation.getMetadata());
            if (metaValue.hasObjectMap()) {
                metaSetter.accept(metaValue.getObjectMap());
            }
        }
    }
}
