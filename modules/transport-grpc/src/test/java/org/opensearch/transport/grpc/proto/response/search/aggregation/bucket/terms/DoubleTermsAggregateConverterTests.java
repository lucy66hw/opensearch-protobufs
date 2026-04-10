/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.DoubleTermsAggregate;
import org.opensearch.protobufs.DoubleTermsBucket;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.BucketOrder;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.bucket.terms.DoubleTerms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregator;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link DoubleTermsAggregateConverter}.
 */
public class DoubleTermsAggregateConverterTests extends OpenSearchTestCase {

    private final DoubleTermsAggregateConverter converter = new DoubleTermsAggregateConverter();

    public void testGetHandledAggregationType() {
        assertEquals(DoubleTerms.class, converter.getHandledAggregationType());
    }

    public void testEmptyBuckets() throws IOException {
        DoubleTerms doubleTerms = createDoubleTerms("test", Collections.emptyList(), 0, 0);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        Aggregate aggregate = result.build();

        assertTrue("Should have dterms set", aggregate.hasDterms());
        DoubleTermsAggregate dterms = aggregate.getDterms();
        assertEquals(0, dterms.getDocCountErrorUpperBound());
        assertEquals(0, dterms.getSumOtherDocCount());
        assertEquals(0, dterms.getBucketsCount());
    }

    public void testSingleBucket() throws IOException {
        DoubleTerms.Bucket bucket = new DoubleTerms.Bucket(3.14, 10, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW);
        DoubleTerms doubleTerms = createDoubleTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        DoubleTermsAggregate dterms = result.build().getDterms();

        assertEquals(1, dterms.getBucketsCount());
        DoubleTermsBucket protoBucket = dterms.getBuckets(0);

        assertEquals(3.14, protoBucket.getKey(), 0.001);
        assertEquals(10L, protoBucket.getDocCount());
        assertFalse("key_as_string should not be set with RAW format", protoBucket.hasKeyAsString());
    }

    public void testMultipleBuckets() throws IOException {
        DoubleTerms.Bucket bucket1 = new DoubleTerms.Bucket(1.0, 100, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW);
        DoubleTerms.Bucket bucket2 = new DoubleTerms.Bucket(2.5, 50, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW);
        DoubleTerms doubleTerms = createDoubleTerms("test", List.of(bucket1, bucket2), 5, 200);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        DoubleTermsAggregate dterms = result.build().getDterms();

        assertEquals(5, dterms.getDocCountErrorUpperBound());
        assertEquals(200, dterms.getSumOtherDocCount());
        assertEquals(2, dterms.getBucketsCount());

        assertEquals(1.0, dterms.getBuckets(0).getKey(), 0.001);
        assertEquals(2.5, dterms.getBuckets(1).getKey(), 0.001);
    }

    public void testBucketWithDocCountError() throws IOException {
        DoubleTerms.Bucket bucket = new DoubleTerms.Bucket(9.99, 100, InternalAggregations.EMPTY, true, 3, DocValueFormat.RAW);
        DoubleTerms doubleTerms = createDoubleTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        DoubleTermsBucket protoBucket = result.build().getDterms().getBuckets(0);

        assertEquals(3L, protoBucket.getDocCountErrorUpperBound());
    }

    public void testBucketWithFormattedKey() throws IOException {
        DocValueFormat format = new DocValueFormat.Decimal("0.00");
        DoubleTerms.Bucket bucket = new DoubleTerms.Bucket(3.14159, 5, InternalAggregations.EMPTY, false, 0, format);
        DoubleTerms doubleTerms = createDoubleTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        DoubleTermsBucket protoBucket = result.build().getDterms().getBuckets(0);

        assertEquals(3.14159, protoBucket.getKey(), 0.00001);
        assertTrue("key_as_string should be set with custom format", protoBucket.hasKeyAsString());
        assertEquals("3.14", protoBucket.getKeyAsString());
    }

    public void testBucketWithRawFormatNoKeyAsString() throws IOException {
        DoubleTerms.Bucket bucket = new DoubleTerms.Bucket(42.0, 10, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW);
        DoubleTerms doubleTerms = createDoubleTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(doubleTerms);
        DoubleTermsBucket protoBucket = result.build().getDterms().getBuckets(0);

        assertFalse("key_as_string should not be set with RAW format", protoBucket.hasKeyAsString());
    }

    private static DoubleTerms createDoubleTerms(String name, List<DoubleTerms.Bucket> buckets, long docCountError, long otherDocCount) {
        return new DoubleTerms(
            name,
            BucketOrder.count(false),
            BucketOrder.count(false),
            Collections.emptyMap(),
            DocValueFormat.RAW,
            10,
            false,
            otherDocCount,
            buckets,
            docCountError,
            new TermsAggregator.BucketCountThresholds(1, 0, 10, -1)
        );
    }
}
