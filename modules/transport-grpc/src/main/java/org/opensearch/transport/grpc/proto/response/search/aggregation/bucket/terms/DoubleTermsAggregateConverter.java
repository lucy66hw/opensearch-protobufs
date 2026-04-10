package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.DoubleTermsAggregate;
import org.opensearch.protobufs.DoubleTermsBucket;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.DoubleTerms;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AggregateProtoUtils;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Proto converter for {@link DoubleTerms}
 */
public class DoubleTermsAggregateConverter implements AggregateProtoConverter {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return DoubleTerms.class;
    }

    @Override
    public Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        DoubleTerms doubleTerms = (DoubleTerms) aggregation;
        DoubleTermsAggregate.Builder termsBuilder = DoubleTermsAggregate.newBuilder();

        termsBuilder.setDocCountErrorUpperBound(doubleTerms.getDocCountError());
        termsBuilder.setSumOtherDocCount(doubleTerms.getSumOfOtherDocCounts());

        for (DoubleTerms.Bucket bucket : doubleTerms.getBuckets()) {
            termsBuilder.addBuckets(convertBucket(bucket));
        }

        TermsAggregateProtoUtils.applyMetadata(termsBuilder::setMeta, doubleTerms);

        return Aggregate.newBuilder().setDterms(termsBuilder);
    }

    /**
     * Mirroring {@link DoubleTerms.Bucket#keyToXContent(XContentBuilder)}
     */
    private DoubleTermsBucket convertBucket(DoubleTerms.Bucket bucket) throws IOException {
        DoubleTermsBucket.Builder builder = DoubleTermsBucket.newBuilder();

        builder.setKey((double) bucket.getKey());

        if (bucket.getFormat() != DocValueFormat.RAW) {
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
