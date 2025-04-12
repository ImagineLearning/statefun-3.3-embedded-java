# The parent Flink image (flink:1.13.2-scala_2.12-java11) only contains the JRE (openjdk:11-jre), and it is missing key
# diagnostic tools. This multistage build will overwrite the JRE with the JDK from openjdk:11
# See https://docs.docker.com/develop/develop-images/multistage-build/
# Add --platform=linux/amd64 to the FROM commands below when on Apple silicon
FROM openjdk:11 as jdk_image
FROM flink:1.16.2-java11

# Copy the JDK from the jdk_image
COPY --from=jdk_image /usr/local/openjdk-11 /usr/local/openjdk-11

RUN sed -i -e 's/^.*networkaddress.cache.ttl=.*$/networkaddress.cache.ttl=30/g' /usr/local/openjdk-11/conf/security/java.security
RUN sed -i -e 's/^.*networkaddress.cache.negative.ttl=.*$/networkaddress.cache.negative.ttl=10/g' /usr/local/openjdk-11/conf/security/java.security

# The 2019 AWS rds root cert
ADD rds-ca-2019-root.pem /etc/rds-ca-2019-root.pem

ENV FLINK_JOB_ARTIFACTS_DIR /opt/artifacts
ENV FLINK_USR_LIB_DIR $FLINK_HOME/usrlib

# Install AWS CLI
RUN apt-get -y update
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install \
    curl \
    python3 \
    python3-venv \
    unzip

# the AWS installer started failing 19 Jul 2021 because it refused to run with python2
RUN rm -f /usr/bin/python && ln -s /usr/bin/python3 /usr/bin/python

RUN wget -O "awscli-bundle.zip" -nv "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" && \
  unzip awscli-bundle.zip && \
  ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws && \
  rm -rf ./awscli-bundle*

# Entry point.
COPY entrypoint.sh /
RUN chmod 775 /entrypoint.sh
RUN sed -i -e 's/\r//g' /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

ENV FLINK_JOB_DIR ${FLINK_HOME}/job
RUN mkdir -p $FLINK_JOB_DIR
COPY target/my-stateful-functions-embedded-java-3.3.0.jar ${FLINK_JOB_DIR}/flink-job.jar
RUN chown -R flink:flink ${FLINK_JOB_DIR}/

ENV PLUGIN_NAME flink-s3-fs-hadoop-1.16.2
RUN mkdir -p "${FLINK_HOME}/plugins/${PLUGIN_NAME}"
RUN ln -fs "${FLINK_HOME}/opt/${PLUGIN_NAME}.jar" "${FLINK_HOME}/plugins/${PLUGIN_NAME}"

# Why do we need this here?
#  The classloader.parent-first-patterns.additional must be in 3 places at runtime in kubernetes
#   1) The jobmanager's flink-conf.yaml, 2) the taskmanager's flink-conf.yaml, 3) the jobsubmitter's flink-conf.yaml
#   The jobmanager and taskmanager both get their flink-conf via the helm chart
#   The jobsubmitter needs it in the docker image.  So yeah, this is for the jobsubmitter.
RUN echo 'classloader.parent-first-patterns.additional: org.apache.flink.statefun;org.apache.kafka;com.google.protobuf' >> ${FLINK_HOME}/conf/flink-conf.yaml

# for some reason related to the job-submitter
# this is the place for checkpointing configuration to be enabled for kubernetes.
RUN echo 'execution.checkpointing.interval: 5minutes' >> ${FLINK_HOME}/conf/flink-conf.yaml
RUN echo 'execution.checkpointing.timeout: 20minutes' >> ${FLINK_HOME}/conf/flink-conf.yaml
RUN echo 'execution.checkpointing.min-pause: 1minutes' >> ${FLINK_HOME}/conf/flink-conf.yaml

