/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.UnsignedLongTermsAggregate;
import org.opensearch.protobufs.UnsignedLongTermsBucket;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.BucketOrder;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregator;
import org.opensearch.search.aggregations.bucket.terms.UnsignedLongTerms;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link UnsignedLongTermsAggregateConverter}.
 */
public class UnsignedLongTermsAggregateConverterTests extends OpenSearchTestCase {

    private final UnsignedLongTermsAggregateConverter converter = new UnsignedLongTermsAggregateConverter();

    public void testGetHandledAggregationType() {
        assertEquals(UnsignedLongTerms.class, converter.getHandledAggregationType());
    }

    public void testEmptyBuckets() throws IOException {
        UnsignedLongTerms terms = createUnsignedLongTerms("test", Collections.emptyList(), 0, 0);

        Aggregate.Builder result = converter.toProto(terms);
        Aggregate aggregate = result.build();

        assertTrue("Should have ulterms set", aggregate.hasUlterms());
        UnsignedLongTermsAggregate ulterms = aggregate.getUlterms();
        assertEquals(0, ulterms.getDocCountErrorUpperBound());
        assertEquals(0, ulterms.getSumOtherDocCount());
        assertEquals(0, ulterms.getBucketsCount());
    }

    public void testSingleBucket() throws IOException {
        UnsignedLongTerms.Bucket bucket = new UnsignedLongTerms.Bucket(
            BigInteger.valueOf(100),
            15,
            InternalAggregations.EMPTY,
            false,
            0,
            DocValueFormat.RAW
        );
        UnsignedLongTerms terms = createUnsignedLongTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(terms);
        UnsignedLongTermsAggregate ulterms = result.build().getUlterms();

        assertEquals(1, ulterms.getBucketsCount());
        UnsignedLongTermsBucket protoBucket = ulterms.getBuckets(0);

        assertEquals(100L, protoBucket.getKey());
        assertEquals(15L, protoBucket.getDocCount());
        assertFalse("key_as_string should not be set with RAW format", protoBucket.hasKeyAsString());
    }

    public void testLargeUnsignedValue() throws IOException {
        BigInteger largeValue = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        UnsignedLongTerms.Bucket bucket = new UnsignedLongTerms.Bucket(
            largeValue,
            5,
            InternalAggregations.EMPTY,
            false,
            0,
            DocValueFormat.RAW
        );
        UnsignedLongTerms terms = createUnsignedLongTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(terms);
        UnsignedLongTermsBucket protoBucket = result.build().getUlterms().getBuckets(0);

        assertEquals(largeValue.longValue(), protoBucket.getKey());
    }

    public void testMultipleBuckets() throws IOException {
        UnsignedLongTerms.Bucket bucket1 = new UnsignedLongTerms.Bucket(
            BigInteger.valueOf(1),
            100,
            InternalAggregations.EMPTY,
            false,
            0,
            DocValueFormat.RAW
        );
        UnsignedLongTerms.Bucket bucket2 = new UnsignedLongTerms.Bucket(
            BigInteger.valueOf(2),
            50,
            InternalAggregations.EMPTY,
            false,
            0,
            DocValueFormat.RAW
        );
        UnsignedLongTerms terms = createUnsignedLongTerms("test", List.of(bucket1, bucket2), 7, 300);

        Aggregate.Builder result = converter.toProto(terms);
        UnsignedLongTermsAggregate ulterms = result.build().getUlterms();

        assertEquals(7, ulterms.getDocCountErrorUpperBound());
        assertEquals(300, ulterms.getSumOtherDocCount());
        assertEquals(2, ulterms.getBucketsCount());
    }

    public void testBucketWithDocCountError() throws IOException {
        UnsignedLongTerms.Bucket bucket = new UnsignedLongTerms.Bucket(
            BigInteger.TEN,
            20,
            InternalAggregations.EMPTY,
            true,
            4,
            DocValueFormat.RAW
        );
        UnsignedLongTerms terms = createUnsignedLongTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(terms);
        UnsignedLongTermsBucket protoBucket = result.build().getUlterms().getBuckets(0);

        assertEquals(4L, protoBucket.getDocCountErrorUpperBound());
    }

    private static UnsignedLongTerms createUnsignedLongTerms(
        String name,
        List<UnsignedLongTerms.Bucket> buckets,
        long docCountError,
        long otherDocCount
    ) {
        return new UnsignedLongTerms(
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
