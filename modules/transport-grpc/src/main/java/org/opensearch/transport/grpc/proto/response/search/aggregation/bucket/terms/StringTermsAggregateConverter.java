package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.StringTermsAggregate;
import org.opensearch.protobufs.StringTermsBucket;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.StringTerms;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AggregateProtoUtils;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Proto converter for {@link StringTerms}
 */
public class StringTermsAggregateConverter implements AggregateProtoConverter {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return StringTerms.class;
    }

    @Override
    public Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        StringTerms stringTerms = (StringTerms) aggregation;
        StringTermsAggregate.Builder termsBuilder = StringTermsAggregate.newBuilder();

        termsBuilder.setDocCountErrorUpperBound(stringTerms.getDocCountError());
        termsBuilder.setSumOtherDocCount(stringTerms.getSumOfOtherDocCounts());

        for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
            termsBuilder.addBuckets(convertBucket(bucket));
        }

        TermsAggregateProtoUtils.applyMetadata(termsBuilder::setMeta, stringTerms);

        return Aggregate.newBuilder().setSterms(termsBuilder);
    }

    /**
     * Mirroring {@link StringTerms.Bucket#keyToXContent(XContentBuilder)}
     */
    private StringTermsBucket convertBucket(StringTerms.Bucket bucket) throws IOException {
        StringTermsBucket.Builder builder = StringTermsBucket.newBuilder();

        builder.setKey(bucket.getKeyAsString());

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
