# The parent Flink image (flink:1.13.2-scala_2.12-java11) only contains the JRE (openjdk:11-jre), and it is missing key
# diagnostic tools. This multistage build will overwrite the JRE with the JDK from openjdk:11
# See https://docs.docker.com/develop/develop-images/multistage-build/
FROM amazon/aws-cli

RUN yum install --quiet -y jq
