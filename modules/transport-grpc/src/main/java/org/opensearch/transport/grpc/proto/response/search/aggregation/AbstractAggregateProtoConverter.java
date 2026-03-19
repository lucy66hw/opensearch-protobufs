/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation;

import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.transport.grpc.proto.response.common.ObjectMapProtoUtils;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Abstract base class for aggregate proto converters that handles metadata centrally.
 * Mirrors the template method pattern of {@link InternalAggregation#toXContent}
 * where {@code toXContent} (final) handles common fields like metadata, and
 * {@code doXContentBody} (abstract) handles type-specific content.
 *
 * <p>Subclasses implement {@link #doProtoBody(InternalAggregation)} for type-specific conversion.
 * Metadata is applied automatically by this base class.
 */
public abstract class AbstractAggregateProtoConverter implements AggregateProtoConverter {

    @Override
    public final Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        Aggregate.Builder builder = doProtoBody(aggregation);
        applyMetadata(builder, aggregation);
        return builder;
    }

    /**
     * Converts the type-specific fields of an InternalAggregation to protobuf.
     * Metadata is handled by the base class — implementations should not set metadata.
     * Mirrors {@link InternalAggregation#doXContentBody}.
     *
     * @param aggregation The InternalAggregation to convert
     * @return An Aggregate.Builder with type-specific fields populated
     * @throws IOException if an error occurs during conversion
     */
    protected abstract Aggregate.Builder doProtoBody(InternalAggregation aggregation) throws IOException;

    private static void applyMetadata(Aggregate.Builder builder, InternalAggregation aggregation) {
        if (aggregation.getMetadata() != null && !aggregation.getMetadata().isEmpty()) {
            ObjectMap.Value metaValue = ObjectMapProtoUtils.toProto(aggregation.getMetadata());
            if (metaValue.hasObjectMap()) {
                builder.setMeta(metaValue.getObjectMap());
            }
        }
    }
}
