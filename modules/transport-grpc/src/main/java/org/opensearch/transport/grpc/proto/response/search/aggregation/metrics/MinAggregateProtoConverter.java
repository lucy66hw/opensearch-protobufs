/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation.metrics;

import org.opensearch.protobufs.Aggregate;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.metrics.InternalMin;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AbstractAggregateProtoConverter;

import java.io.IOException;

/**
 * Converter for {@link InternalMin} aggregations to Protocol Buffer Aggregate messages.
 * Delegates the actual conversion logic to {@link MinAggregateProtoUtils}.
 */
public class MinAggregateProtoConverter extends AbstractAggregateProtoConverter {

    /**
     * Creates a new MinAggregateProtoConverter.
     */
    public MinAggregateProtoConverter() {}

    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return InternalMin.class;
    }

    @Override
    protected Aggregate.Builder doProtoBody(InternalAggregation aggregation) throws IOException {
        Aggregate aggregate = MinAggregateProtoUtils.toProto((InternalMin) aggregation);
        return aggregate.toBuilder();
    }
}
