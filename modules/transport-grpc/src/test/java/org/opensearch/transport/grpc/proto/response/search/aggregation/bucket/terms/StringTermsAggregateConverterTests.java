/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.apache.lucene.util.BytesRef;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.StringTermsAggregate;
import org.opensearch.protobufs.StringTermsBucket;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.BucketOrder;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.bucket.terms.StringTerms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregator;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link StringTermsAggregateConverter}.
 */
public class StringTermsAggregateConverterTests extends OpenSearchTestCase {

    private final StringTermsAggregateConverter converter = new StringTermsAggregateConverter();

    public void testGetHandledAggregationType() {
        assertEquals(StringTerms.class, converter.getHandledAggregationType());
    }

    public void testEmptyBuckets() throws IOException {
        StringTerms stringTerms = createStringTerms("test", Collections.emptyList(), 0, 0);

        Aggregate.Builder result = converter.toProto(stringTerms);
        Aggregate aggregate = result.build();

        assertTrue("Should have sterms set", aggregate.hasSterms());
        StringTermsAggregate sterms = aggregate.getSterms();
        assertEquals(0, sterms.getDocCountErrorUpperBound());
        assertEquals(0, sterms.getSumOtherDocCount());
        assertEquals(0, sterms.getBucketsCount());
    }

    public void testSingleBucket() throws IOException {
        StringTerms.Bucket bucket = new StringTerms.Bucket(
            new BytesRef("active"), 25, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW
        );
        StringTerms stringTerms = createStringTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(stringTerms);
        StringTermsAggregate sterms = result.build().getSterms();

        assertEquals(1, sterms.getBucketsCount());
        StringTermsBucket protoBucket = sterms.getBuckets(0);

        assertEquals("active", protoBucket.getKey());
        assertEquals(25L, protoBucket.getDocCount());
    }

    public void testMultipleBuckets() throws IOException {
        StringTerms.Bucket bucket1 = new StringTerms.Bucket(
            new BytesRef("active"), 100, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW
        );
        StringTerms.Bucket bucket2 = new StringTerms.Bucket(
            new BytesRef("inactive"), 50, InternalAggregations.EMPTY, false, 0, DocValueFormat.RAW
        );
        StringTerms stringTerms = createStringTerms("test", List.of(bucket1, bucket2), 3, 150);

        Aggregate.Builder result = converter.toProto(stringTerms);
        StringTermsAggregate sterms = result.build().getSterms();

        assertEquals(3, sterms.getDocCountErrorUpperBound());
        assertEquals(150, sterms.getSumOtherDocCount());
        assertEquals(2, sterms.getBucketsCount());

        assertEquals("active", sterms.getBuckets(0).getKey());
        assertEquals("inactive", sterms.getBuckets(1).getKey());
    }

    public void testBucketWithDocCountError() throws IOException {
        StringTerms.Bucket bucket = new StringTerms.Bucket(
            new BytesRef("error_test"), 10, InternalAggregations.EMPTY, true, 2, DocValueFormat.RAW
        );
        StringTerms stringTerms = createStringTerms("test", List.of(bucket), 0, 0);

        Aggregate.Builder result = converter.toProto(stringTerms);
        StringTermsBucket protoBucket = result.build().getSterms().getBuckets(0);

        assertEquals(2L, protoBucket.getDocCountErrorUpperBound());
    }

    private static StringTerms createStringTerms(String name, List<StringTerms.Bucket> buckets, long docCountError, long otherDocCount) {
        return new StringTerms(
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
