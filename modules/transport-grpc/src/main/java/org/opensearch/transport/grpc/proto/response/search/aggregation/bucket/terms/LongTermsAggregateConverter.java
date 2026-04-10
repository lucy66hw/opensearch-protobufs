package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.LongTermsAggregate;
import org.opensearch.protobufs.LongTermsBucket;
import org.opensearch.protobufs.LongTermsBucketKey;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.InternalTerms;
import org.opensearch.search.aggregations.bucket.terms.LongTerms;
import org.opensearch.transport.grpc.proto.response.common.ObjectMapProtoUtils;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AggregateProtoUtils;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Proto converter for {@link LongTerms}
 */
public class LongTermsAggregateConverter implements AggregateProtoConverter {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return LongTerms.class;
    }

    @Override
    public Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        LongTerms longTerms = (LongTerms) aggregation;
        LongTermsAggregate.Builder termsBuilder = LongTermsAggregate.newBuilder();

        termsBuilder.setDocCountErrorUpperBound(longTerms.getDocCountError());
        termsBuilder.setSumOtherDocCount(longTerms.getSumOfOtherDocCounts());

        for (LongTerms.Bucket bucket : longTerms.getBuckets()) {
            termsBuilder.addBuckets(convertBucket(bucket));
        }

        TermsAggregateProtoUtils.applyMetadata(termsBuilder::setMeta, longTerms);

        return Aggregate.newBuilder().setLterms(termsBuilder);
    }

    /**
     * Mirroring {@link LongTerms.Bucket#keyToXContent(XContentBuilder)}
     */
    private LongTermsBucket convertBucket(LongTerms.Bucket bucket) throws IOException {
        LongTermsBucket.Builder builder = LongTermsBucket.newBuilder();

        Object key = bucket.getKey();
        if (key instanceof Long) {
            builder.setKey(LongTermsBucketKey.newBuilder().setSigned((long) key));
        } else {
            builder.setKey(LongTermsBucketKey.newBuilder().setUnsigned(key.toString()));
        }

        if (bucket.getFormat() != DocValueFormat.RAW && bucket.getFormat() != DocValueFormat.UNSIGNED_LONG_SHIFTED) {
            builder.setKeyAsString(bucket.getKeyAsString());
        }

        builder.setDocCount(bucket.getDocCount());
        if (bucket.showDocCountError()) {
            builder.setDocCountErrorUpperBound(bucket.getDocCountError());
        }

        for (Aggregation subAgg : bucket.getAggregations()) {
            if (subAgg instanceof InternalAggregation internalAgg) {
                builder.getMutableAggregate().put(subAgg.getName(), AggregateProtoUtils.toProto(internalAgg));
            }
        }

        return builder.build();
    }
}
