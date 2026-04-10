package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.UnsignedLongTermsAggregate;
import org.opensearch.protobufs.UnsignedLongTermsBucket;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.UnsignedLongTerms;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AggregateProtoUtils;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Proto converter for {@link UnsignedLongTerms}
 */
public class UnsignedLongTermsAggregateConverter implements AggregateProtoConverter {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return UnsignedLongTerms.class;
    }

    @Override
    public Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        UnsignedLongTerms unsignedLongTerms = (UnsignedLongTerms) aggregation;
        UnsignedLongTermsAggregate.Builder termsBuilder = UnsignedLongTermsAggregate.newBuilder();

        termsBuilder.setDocCountErrorUpperBound(unsignedLongTerms.getDocCountError());
        termsBuilder.setSumOtherDocCount(unsignedLongTerms.getSumOfOtherDocCounts());

        for (UnsignedLongTerms.Bucket bucket : unsignedLongTerms.getBuckets()) {
            termsBuilder.addBuckets(convertBucket(bucket));
        }

        TermsAggregateProtoUtils.applyMetadata(termsBuilder::setMeta, unsignedLongTerms);

        return Aggregate.newBuilder().setUlterms(termsBuilder);
    }

    /**
     * Mirroring {@link UnsignedLongTerms.Bucket#keyToXContent(XContentBuilder)}
     */
    private UnsignedLongTermsBucket convertBucket(UnsignedLongTerms.Bucket bucket) throws IOException {
        UnsignedLongTermsBucket.Builder builder = UnsignedLongTermsBucket.newBuilder();

        builder.setKey(((Number) bucket.getKey()).longValue());

        if (bucket.getFormat() != DocValueFormat.RAW
            && bucket.getFormat() != DocValueFormat.UNSIGNED_LONG
            && bucket.getFormat() != DocValueFormat.UNSIGNED_LONG_SHIFTED) {
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
