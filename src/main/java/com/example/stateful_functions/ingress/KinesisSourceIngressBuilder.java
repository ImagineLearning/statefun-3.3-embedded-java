package com.example.stateful_functions.ingress;

import com.example.stateful_functions.Configuration;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.kinesis.shaded.com.amazonaws.regions.DefaultAwsRegionProviderChain;
import org.apache.flink.statefun.flink.common.UnimplementedTypeInfo;
import org.apache.flink.statefun.flink.io.datastream.SourceFunctionSpec;
import org.apache.flink.statefun.sdk.io.IngressIdentifier;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsCredentials;
import org.apache.flink.statefun.sdk.kinesis.auth.AwsRegion;
import org.apache.flink.statefun.sdk.kinesis.ingress.IngressRecord;
import org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressDeserializer;
import org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressSpec;
import org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressStartupPosition;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.serialization.KinesisDeserializationSchema;
import org.apache.flink.streaming.connectors.kinesis.util.AWSUtil;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;


/**
 *
 *  Per flink mailing list, KinesisIngressBuilder not being able to set things like ConsumerConfigConstants was an oversight
 *    https://lists.apache.org/thread.html/rb38d86ca097148d7282cb8cf9668536d9b990d0791e6c4050e9a51a6%40%3Cuser.flink.apache.org%3E
 *
 *  flink jira ticket
 *    https://issues.apache.org/jira/browse/FLINK-22529
 *
 *  Workaround this limitation using SourceFunctionSpec instead of KinesisIngressBuilder
 *    KinesisSourceIngressBuilder is meant to mimic KinesisIngressBuilder
 *
 */


public final class KinesisSourceIngressBuilder<T> {

    private final IngressIdentifier<T> id;

    private final List<String> streams = new ArrayList<>();
    private KinesisIngressDeserializer<T> deserializer;
    private KinesisIngressStartupPosition startupPosition =
            KinesisIngressStartupPosition.fromLatest();
    private AwsRegion awsRegion = AwsRegion.fromDefaultProviderChain();
    private AwsCredentials awsCredentials = AwsCredentials.fromDefaultProviderChain();
    private final Properties clientConfigurationProperties = new Properties();
    private final Properties consumerConfigProperties = new Properties();

    private KinesisSourceIngressBuilder(IngressIdentifier<T> id) {
        this.id = Objects.requireNonNull(id);
    }

    /**
     * @param id  A unique ingress identifier.
     * @param <T> The type consumed from Kinesis.
     * @return A new {@link org.apache.flink.statefun.sdk.kinesis.ingress.KinesisIngressBuilder}.
     */
    public static <T> KinesisSourceIngressBuilder<T> forIdentifier(IngressIdentifier<T> id) {
        return new KinesisSourceIngressBuilder<>(id);
    }

    /**
     * @param stream The name of a stream that should be consumed.
     */
    public KinesisSourceIngressBuilder<T> withStream(String stream) {
        this.streams.add(stream);
        return this;
    }

    /**
     * @param streams A list of streams that should be consumed.
     */
    public KinesisSourceIngressBuilder<T> withStreams(List<String> streams) {
        this.streams.addAll(streams);
        return this;
    }

    /**
     * @param deserializerClass The deserializer used to convert between Kinesis's byte messages and
     *                          Java objects.
     */
    public KinesisSourceIngressBuilder<T> withDeserializer(
            Class<? extends KinesisIngressDeserializer<T>> deserializerClass) {
        Objects.requireNonNull(deserializerClass);
        this.deserializer = instantiateDeserializer(deserializerClass);
        return this;
    }

    /**
     * Configures the position that the ingress should start consuming from. By default, the startup
     * position is {@link KinesisIngressStartupPosition#fromLatest()}.
     *
     * <p>Note that this configuration only affects the position when starting the application from a
     * fresh start. When restoring the application from a savepoint, the ingress will always start
     * consuming from the position persisted in the savepoint.
     *
     * @param startupPosition the position that the Kafka ingress should start consuming from.
     * @see KinesisIngressStartupPosition
     */
    public KinesisSourceIngressBuilder<T> withStartupPosition(
            KinesisIngressStartupPosition startupPosition) {
        this.startupPosition = Objects.requireNonNull(startupPosition);
        return this;
    }

    /**
     * The AWS region to connect to. By default, AWS's default provider chain is consulted.
     *
     * @param awsRegion The AWS region to connect to.
     * @see <a
     * href="https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html#automatically-determine-the-aws-region-from-the-environment">Automatically
     * Determine the AWS Region from the Environment</a>.
     * @see AwsRegion
     */
    public KinesisSourceIngressBuilder<T> withAwsRegion(AwsRegion awsRegion) {
        this.awsRegion = Objects.requireNonNull(awsRegion);
        return this;
    }

    /**
     * The AWS region to connect to, specified by the AWS region's unique id. By default, AWS's
     * default provider chain is consulted.
     *
     * @param regionName The unique id of the AWS region to connect to.
     */
    public KinesisSourceIngressBuilder<T> withAwsRegion(String regionName) {
        this.awsRegion = AwsRegion.ofId(regionName);
        return this;
    }

    /**
     * The AWS credentials to use. By default, AWS's default provider chain is consulted.
     *
     * @param awsCredentials The AWS credentials to use.
     * @see <a
     * href="https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default">Using
     * the Default Credential Provider Chain</a>.
     * @see AwsCredentials
     */
    public KinesisSourceIngressBuilder<T> withAwsCredentials(AwsCredentials awsCredentials) {
        this.awsCredentials = Objects.requireNonNull(awsCredentials);
        return this;
    }

    /**
     * Sets a AWS client configuration to be used by the ingress.
     *
     * <p>Supported values are properties of AWS's <a
     * href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html">com.aws.ClientConfiguration</a>.
     * For example, to set a value for {@code SOCKET_TIMEOUT}, the property key would be {@code
     * SocketTimeout}.
     *
     * @param key   the property to set.
     * @param value the value for the property.
     * @see <a
     * href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html">com.aws.ClientConfiguration</a>.
     */
    public KinesisSourceIngressBuilder<T> withClientConfigurationProperty(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.clientConfigurationProperties.setProperty(asFlinkConsumerClientPropertyKey(key), value);
        return this;
    }


    public KinesisSourceIngressBuilder<T> withConsumerProperty(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.consumerConfigProperties.setProperty(key, value);
        return this;
    }

    public KinesisSourceIngressBuilder<T> useEnhancedFanout(Boolean enabled) {
        if (enabled) {
            this.consumerConfigProperties.setProperty(ConsumerConfigConstants.RECORD_PUBLISHER_TYPE, ConsumerConfigConstants.RecordPublisherType.EFO.name());
            this.consumerConfigProperties.setProperty(ConsumerConfigConstants.EFO_CONSUMER_NAME, Configuration.ENHANCED_FANOUT_NAME);
        }
        return this;
    }

    /**
     * @return A new {@link KinesisIngressSpec}.
     */
    public SourceFunctionSpec<T> build() {
        Properties mergedProperties = new Properties();
        mergedProperties.putAll(consumerConfigProperties);
        mergedProperties.putAll(clientConfigurationProperties);
        mergedProperties.putAll(forAwsRegionConsumerProps(awsRegion));
        mergedProperties.putAll(forAwsCredentials(awsCredentials));
        setStartupPositionProperties(mergedProperties, startupPosition);

        FlinkKinesisConsumer<T> flinkKinesisConsumer = new FlinkKinesisConsumer<T>(streams,
                new KinesisDeserializationSchemaDelegate(deserializer), mergedProperties);
        return new SourceFunctionSpec<>(id, flinkKinesisConsumer);
    }

    // ========================================================================================
    //  Methods for runtime usage
    // ========================================================================================

    KinesisSourceIngressBuilder<T> withDeserializer(KinesisIngressDeserializer<T> deserializer) {
        this.deserializer = Objects.requireNonNull(deserializer);
        return this;
    }

    // ========================================================================================
    //  Utility methods
    // ========================================================================================

    private static <T extends KinesisIngressDeserializer<?>> T instantiateDeserializer(
            Class<T> deserializerClass) {
        try {
            Constructor<T> defaultConstructor = deserializerClass.getDeclaredConstructor();
            defaultConstructor.setAccessible(true);
            return defaultConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Unable to create an instance of deserializer "
                            + deserializerClass.getName()
                            + "; has no default constructor",
                    e);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Unable to create an instance of deserializer " + deserializerClass.getName(), e);
        }
    }


    // Copied from KinesisDeserializationSchemaDelegate.java in statefun
    static final class KinesisDeserializationSchemaDelegate<T> implements KinesisDeserializationSchema<T> {

        private static final long serialVersionUID = 1L;

        private final TypeInformation<T> producedTypeInfo = new UnimplementedTypeInfo<>();
        private final KinesisIngressDeserializer<T> delegate;

        KinesisDeserializationSchemaDelegate(KinesisIngressDeserializer<T> delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public T deserialize(
                byte[] recordValue,
                String partitionKey,
                String seqNum,
                long approxArrivalTimestamp,
                String stream,
                String shardId)
                throws IOException {
            return delegate.deserialize(
                    IngressRecord.newBuilder()
                            .withData(recordValue)
                            .withStream(stream)
                            .withShardId(shardId)
                            .withPartitionKey(partitionKey)
                            .withSequenceNumber(seqNum)
                            .withApproximateArrivalTimestamp(approxArrivalTimestamp)
                            .build());
        }

        @Override
        public TypeInformation<T> getProducedType() {
            return producedTypeInfo;
        }
    }

    // From AwsAuthConfigProperties.java in statefun
    static Properties forAwsCredentials(AwsCredentials awsCredentials) {
        final Properties properties = new Properties();

        if (awsCredentials.isDefault()) {
            properties.setProperty(
                    AWSConfigConstants.AWS_CREDENTIALS_PROVIDER,
                    AWSConfigConstants.CredentialProvider.AUTO.name());
        } else if (awsCredentials.isBasic()) {
            properties.setProperty(
                    AWSConfigConstants.AWS_CREDENTIALS_PROVIDER,
                    AWSConfigConstants.CredentialProvider.BASIC.name());

            final AwsCredentials.BasicAwsCredentials basicCredentials = awsCredentials.asBasic();
            properties.setProperty(
                    AWSConfigConstants.accessKeyId(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER),
                    basicCredentials.accessKeyId());
            properties.setProperty(
                    AWSConfigConstants.secretKey(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER),
                    basicCredentials.secretAccessKey());
        } else if (awsCredentials.isProfile()) {
            properties.setProperty(
                    AWSConfigConstants.AWS_CREDENTIALS_PROVIDER,
                    AWSConfigConstants.CredentialProvider.PROFILE.name());

            final AwsCredentials.ProfileAwsCredentials profileCredentials = awsCredentials.asProfile();
            properties.setProperty(
                    AWSConfigConstants.profileName(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER),
                    profileCredentials.name());
            profileCredentials
                    .path()
                    .ifPresent(
                            path ->
                                    properties.setProperty(
                                            AWSConfigConstants.profilePath(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER),
                                            path));
        } else {
            throw new IllegalStateException(
                    "Unrecognized AWS credentials configuration type: " + awsCredentials);
        }

        return properties;
    }

    // From AwsAuthConfigProperties.java in statefun
    static Properties forAwsRegionConsumerProps(AwsRegion awsRegion) {
        final Properties properties = new Properties();

        if (awsRegion.isDefault()) {
            properties.setProperty(AWSConfigConstants.AWS_REGION, new DefaultAwsRegionProviderChain().getRegion().toLowerCase(Locale.ENGLISH));
        } else if (awsRegion.isId()) {
            properties.setProperty(AWSConfigConstants.AWS_REGION, awsRegion.asId().id());
        } else if (awsRegion.isCustomEndpoint()) {
            final AwsRegion.CustomEndpointAwsRegion customEndpoint = awsRegion.asCustomEndpoint();
            properties.setProperty(AWSConfigConstants.AWS_ENDPOINT, customEndpoint.serviceEndpoint());
            properties.setProperty(AWSConfigConstants.AWS_REGION, customEndpoint.regionId());
        } else {
            throw new IllegalStateException("Unrecognized AWS region configuration type: " + awsRegion);
        }

        return properties;
    }

    // From KinesisSourceProvider.java in statefun
    private static void setStartupPositionProperties(
            Properties properties, KinesisIngressStartupPosition startupPosition) {
        if (startupPosition.isEarliest()) {
            properties.setProperty(
                    ConsumerConfigConstants.STREAM_INITIAL_POSITION,
                    ConsumerConfigConstants.InitialPosition.TRIM_HORIZON.name());
        } else if (startupPosition.isLatest()) {
            properties.setProperty(
                    ConsumerConfigConstants.STREAM_INITIAL_POSITION,
                    ConsumerConfigConstants.InitialPosition.LATEST.name());
        } else if (startupPosition.isDate()) {
            properties.setProperty(
                    ConsumerConfigConstants.STREAM_INITIAL_POSITION,
                    ConsumerConfigConstants.InitialPosition.AT_TIMESTAMP.name());

            final ZonedDateTime startupDate = startupPosition.asDate().date();
            final DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(ConsumerConfigConstants.DEFAULT_STREAM_TIMESTAMP_DATE_FORMAT);
            properties.setProperty(
                    ConsumerConfigConstants.STREAM_INITIAL_TIMESTAMP, startupDate.format(formatter));
        } else {
            throw new IllegalStateException(
                    "Unrecognized ingress startup position type: " + startupPosition);
        }
    }


    // copied from KinesisSourceProvider.java

    private static String asFlinkConsumerClientPropertyKey(String key) {
        return AWSUtil.AWS_CLIENT_CONFIG_PREFIX + lowercaseFirstLetter(key);
    }

    private static String lowercaseFirstLetter(String string) {
        final char[] chars = string.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}

