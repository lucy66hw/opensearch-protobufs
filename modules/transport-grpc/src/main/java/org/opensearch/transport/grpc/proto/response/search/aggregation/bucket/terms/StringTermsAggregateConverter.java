package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.StringTerms;

import org.opensearch.transport.grpc.proto.response.common.ObjectMapProtoUtils;

import java.io.IOException;

/**
 * Proto converter for {@link StringTerms}
 */
public class StringTermsAggregateConverter extends TermsAggregatesProtoConverter<StringTerms.Bucket> {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return StringTerms.class;
    }

    @Override
    protected Aggregate.Builder doProtoBody(InternalAggregation aggregation) throws IOException {
        StringTerms stringTerms = (StringTerms) aggregation;
        Aggregate.Builder protoBuilder = Aggregate.newBuilder();
        convertCommon(protoBuilder, stringTerms.getDocCountError(), stringTerms.getSumOfOtherDocCounts(), stringTerms.getBuckets());
        return protoBuilder;
    }

    /**
     * Mirroring {@link StringTerms.Bucket#keyToXContent(XContentBuilder)}
     *
     * {@inheritDoc}
     */
    @Override
    void convertBucketKey(ObjectMap.Builder builder, StringTerms.Bucket bucket) {
        builder.putFields(Aggregation.CommonFields.KEY.getPreferredName(), ObjectMapProtoUtils.toProto(bucket.getKeyAsString()));
    }
}
