package org.opensearch.transport.grpc.proto.response.search.aggregation.bucket.terms;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.protobufs.Aggregate;
import org.opensearch.protobufs.UnmappedTermsAggregate;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.bucket.terms.UnmappedTerms;
import org.opensearch.transport.grpc.spi.AggregateProtoConverter;

import java.io.IOException;

/**
 * Proto converter for {@link UnmappedTerms}.
 * Mirroring {@link UnmappedTerms#doXContentBody(XContentBuilder, org.opensearch.core.xcontent.ToXContent.Params)}
 * which writes zero doc_count_error_upper_bound, zero sum_other_doc_count, and an empty buckets array.
 */
public class UnmappedTermsAggregateConverter implements AggregateProtoConverter {
    @Override
    public Class<? extends InternalAggregation> getHandledAggregationType() {
        return UnmappedTerms.class;
    }

    @Override
    public Aggregate.Builder toProto(InternalAggregation aggregation) throws IOException {
        UnmappedTermsAggregate.Builder termsBuilder = UnmappedTermsAggregate.newBuilder();
        termsBuilder.setDocCountErrorUpperBound(0);
        termsBuilder.setSumOtherDocCount(0);

        TermsAggregateProtoUtils.applyMetadata(termsBuilder::setMeta, aggregation);

        return Aggregate.newBuilder().setUmterms(termsBuilder);
    }
}
