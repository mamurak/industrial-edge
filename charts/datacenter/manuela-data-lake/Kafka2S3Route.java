// dependency=camel:camel-endpointdsl
package com.redhat.manuela.routes;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.builder.endpoint.dsl.AWS2S3EndpointBuilderFactory;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kafka2S3Route extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(Kafka2S3Route.class);

    @PropertyInject("kafka.broker.uri")
    private String kafka_broker_uri;
    @PropertyInject("kafka.broker.topic.vibration")
    private String kafka_broker_topic_vibration;
    @PropertyInject("kafka.broker.topic.temperature")
    private String kafka_broker_topic_temperature;

    @PropertyInject("s3.custom.endpoint.enabled")
    private String s3_custom_endpoint_enabled;

    @PropertyInject("s3.custom.endpoint.url")
    private String s3_custom_endpoint_url;

    @PropertyInject("AWS_ACCESS_KEY_ID")
    private String s3_accessKey;
    @PropertyInject("AWS_SECRET_ACCESS_KEY")
    private String s3_secretKey;
    @PropertyInject("s3.message.aggregation.count")
    private String s3_message_aggregation_count;

    @PropertyInject("s3.bucket.name")
    private String s3_bucket_name;
    @PropertyInject("s3.region")
    private String s3_region;
    @Override
    public void configure() throws Exception {
        storeTemperatureInS3();
        storeVibrationInS3();
    }

    private void storeVibrationInS3() {
        String key = "accessKey=RAW(" + s3_accessKey + ")";
        String secret = "&secretKey=RAW(" + s3_secretKey + ")";
        String region = "&region=" + s3_region;
        String customendpoint = "&overrideEndpoint=" + s3_custom_endpoint_enabled;
        String endpoint = "&uriEndpointOverride=" + s3_custom_endpoint_url;
        String s3params = key
        + customendpoint
        + "&forcePathStyle=true"
        + endpoint
        + secret
        + region;

        from("kafka:" + kafka_broker_topic_vibration + "?brokers=" + kafka_broker_uri)
            .convertBodyTo(String.class)
            .aggregate(simple("true"), new GroupedBodyAggregationStrategy()).completionSize(s3_message_aggregation_count)
            .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        List<Exchange> data = exchange.getIn().getBody(List.class);
                        StringBuffer sb = new StringBuffer();
                        for (Iterator iterator = data.iterator(); iterator.hasNext();) {
                            String ex = (String) iterator.next();
                            sb.append(ex+"\\n");
                        }
                        exchange.getIn().setBody(new ByteArrayInputStream(sb.toString().getBytes()));
                    }
                })
            // .to(\"file:/var/tmp/\");
            .setHeader(AWS2S3Constants.KEY, simple("manuela-prod-vibration-${headers[kafka.KEY]}-${date:now}.txt"))
            .to("aws2-s3://" + s3_bucket_name + "?" + s3params)
            .log("Uploaded from [ ${headers[kafka.KEY]} ] Vibration dataset to S3");
    }
    private void storeTemperatureInS3() {
        String key = "accessKey=RAW(" + s3_accessKey + ")";
        String secret = "&secretKey=RAW(" + s3_secretKey + ")";
        String region = "&region=" + s3_region;
        String customendpoint = "&overrideEndpoint=" + s3_custom_endpoint_enabled;
        String endpoint = "&uriEndpointOverride=" + s3_custom_endpoint_url;
        String s3params = key
        + customendpoint
        + "&forcePathStyle=true"
        + endpoint
        + secret
        + region;
        from("kafka:" + kafka_broker_topic_temperature + "?brokers=" + kafka_broker_uri)
            .convertBodyTo(String.class)
            .aggregate(simple("true"), new GroupedBodyAggregationStrategy()).completionSize(s3_message_aggregation_count)
            .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        List<Exchange> data = exchange.getIn().getBody(List.class);
                        StringBuffer sb = new StringBuffer();
                        for (Iterator iterator = data.iterator(); iterator.hasNext();) {
                            String ex = (String) iterator.next();
                            sb.append(ex+"\n");
                        }
                        exchange.getIn().setBody(new ByteArrayInputStream(sb.toString().getBytes()));
                    }
                })
            // .to(\"file:/var/tmp/\");
            .setHeader(AWS2S3Constants.KEY, simple("manuela-prod-temperature-${headers[kafka.KEY]}-${date:now}.txt"))
            .to("aws2-s3://" + s3_bucket_name + "?" + s3params)
            .log("Uploaded Temperature from [ ${headers[kafka.KEY]} ] dataset to S3");
    }
    @Override
    public OnCompletionDefinition onCompletion() {
        return super.onCompletion();
    }
}
