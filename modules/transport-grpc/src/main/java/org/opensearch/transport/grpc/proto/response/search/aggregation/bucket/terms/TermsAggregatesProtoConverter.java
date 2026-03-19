package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.InternalMappedTerms;
import org.opensearch.search.aggregations.bucket.terms.InternalTerms;
import org.opensearch.transport.grpc.proto.response.common.ObjectMapProtoUtils;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AbstractAggregateProtoConverter;
import org.opensearch.transport.grpc.proto.response.search.aggregation.AggregateProtoUtils;

import java.io.IOException;
import java.util.List;

/**
 * A common base implementation for TermsAggregation, child classes will only need to implement {@link #convertBucketKey(ObjectMap.Builder, InternalTerms.Bucket)}
 * and call {@link #convertCommon(Aggregate.Builder, long, long, List)} accordingly
 *
 * @param <B> bucket type for each subclass
 */
public abstract class TermsAggregatesProtoConverter<B extends InternalTerms.Bucket<B>> extends AbstractAggregateProtoConverter {

    /**
     * Mirroring {@link InternalMappedTerms#doXContentBody(XContentBuilder, ToXContent.Params)}
     */
    public void convertCommon(Aggregate.Builder protoBuilder, long docCountError, long otherDocCount, List<B> buckets) throws IOException {
        protoBuilder.setDocCountErrorUpperBound(docCountError);
        protoBuilder.setSumOtherDocCount(otherDocCount);
        for (B bucket : buckets) {
            protoBuilder.addBuckets(convertBucket(bucket));
        }
    }

    /**
     * Mirroring {@link org.opensearch.search.aggregations.bucket.terms.InternalTerms.Bucket#toXContent(XContentBuilder, ToXContent.Params)}
     */
    public ObjectMap convertBucket(B bucket) throws IOException {
        ObjectMap.Builder builder = ObjectMap.newBuilder();
        convertBucketKey(builder, bucket);
        builder.putFields(Aggregation.CommonFields.DOC_COUNT.getPreferredName(), ObjectMapProtoUtils.toProto(bucket.getDocCount()));
        if (bucket.showDocCountError()) {
            builder.putFields(
                InternalTerms.DOC_COUNT_ERROR_UPPER_BOUND_FIELD_NAME.getPreferredName(),
                ObjectMapProtoUtils.toProto(bucket.getDocCountError())
            );
        }
        for (Aggregation aggregation : bucket.getAggregations()) {
            if (aggregation instanceof InternalAggregation internalAggregation) {
                Aggregate aggregate = AggregateProtoUtils.toProto(internalAggregation);
                // NOTE: this is slightly different from InternalAggregation#toXContent
                // where the name might be joined together with the type depending on the parameter
                // of toXContent
                builder.putFields(aggregation.getName(), newValue(aggregateToObjectMap(aggregate)));
            }
        }
        return builder.build();
    }

    abstract void convertBucketKey(ObjectMap.Builder builder, B bucket);

    private static ObjectMap aggregateToObjectMap(Aggregate aggregate) {
        ObjectMap.Builder builder = ObjectMap.newBuilder();
        builder.putFields("meta", ObjectMap.Value.newBuilder().setObjectMap(aggregate.getMeta()).build());
        ObjectMap.ListValue.Builder bucketsBuilder = ObjectMap.ListValue.newBuilder();
        for (ObjectMap bucket : aggregate.getBucketsList()) {
            bucketsBuilder.addValue(ObjectMap.Value.newBuilder().setObjectMap(bucket).build());
        }
        builder.putFields("buckets", ObjectMap.Value.newBuilder().setListValue(bucketsBuilder.build()).build());
        builder.putFields("doc_count_error_upper_bound", ObjectMapProtoUtils.toProto(aggregate.getDocCountErrorUpperBound()));
        builder.putFields("sum_other_doc_count", ObjectMapProtoUtils.toProto(aggregate.getSumOtherDocCount()));
        if (aggregate.getValue().hasDouble()) {
            builder.putFields("value", ObjectMapProtoUtils.toProto(aggregate.getValue().getDouble()));
        } else {
            builder.putFields("value", aggregate.getValue());
        }
        builder.putFields("value_as_string", ObjectMapProtoUtils.toProto(aggregate.getValueAsString()));
        return builder.build();
    }
}
