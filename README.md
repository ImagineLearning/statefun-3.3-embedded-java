# Example Flink Stateful Functions Java Project

## Overview

This is an example of a Flink Stateful Functions project implemented in Java which uses the statefun embedded sdk.

The purpose of this project is two-fold.  
 1. It demonstrates how Imagine Learning implements stateful functions, with only a few key differences, namely
    * This project uses JSON-formatted [CloudEvents](https://github.com/cloudevents/spec?tab=readme-ov-file#cloudevents) 
      because it avoids the messy deserialization required when processing 
      [Caliper](https://www.imsglobal.org/spec/caliper/v1p2) events.
    * This project egresses results as events to a separate stream, whereas at Imagine Learning we mostly send our 
      results directly to OpenSearch and occasionally write events back to the ingress stream.
 2. It will serve as the basis for an evaluation of Stateful Functions running on 
    [AWS Managed Flink](https://docs.aws.amazon.com/managed-flink/).  At the time of
    this writing Imagine Learning runs stateful functions on self-managed Kubernetes clusters, but we are looking to
    see if AWS Managed Flink is a viable alternative.


This project demonstrates stateful functions under test in various ways:
  * build-time isolated function tests in which a single function is tested using events defined in a test resource file 
  * build-time integration tests in which several functions at once are tested using events from a test resource file
  * run-time execution in standalone job mode via docker compose


The project implements embedded functions (functions that execute in the Flink taskmanagers).  Remote functions are future work.

This is an opinionated project.  It uses...
  * Spring Framework for dependency injection
  * Kinesis streams for ingress/egress
  * CloudEvents serialized to JSON are the Kinesis payloads
  * Protobuf envelopes wrap the CloudEvents between ingress deserialization and egress serialization.
  * The router logic and stateful functions work with the CloudEvents, not the Protobuf envelopes.
  * The custom annotation `@StatefunFunction` is used to find the stateful
    function implementations and bind them without having to write boilerplate code.
  * The router implementation relies on Spring to provide the list of 
    `com.example.stateful_functions.router.Forwarder` implementations.  
    Each forwarder is small piece of code that routes one or more specific event types
    to a stateful function.  To start routing a new event type, just implement another Forwarder.

## What this Stateful Functions appication does
Example events and functions are provided which demonstrate notifying a shopping cart service of 
product price and availability changes for items in users' carts.  The project assumes the 
existence of upstream microservices that send Product events (name,price,availability) and
Cart Action events (add product to cart, delete cart, etc), and a 
downstream service which handles cart item price and availability changes.  Two 
functions are provided... 1) a product function which stores the
current details for each product, including price and availability, 
2) A cart function which interacts with the product function via function-to-function
messaging.  The cart function egresses a cart status event containing the information
required to display product price/availability changes to the user.

This project demonstrates the following event scenario:
  * The product service sends a product event to declare the product name, description, 
    price, and availability.
  * The cart service sends a cart product event when a product is added to the cart.
  * Product events are routed to the product function
  * Cart product events are routed to the cart function
  * The cart function subscribes to the product via internal messaging to the product function
  * Later, a product event is sent which indicates an increase of the price of the product
  * The product function sends the latest product info to the subscribers (i.e, the cart function)
  * The cart function compares the two prices, and egresses a cart status event 
    with the price discrepancy for the product.
  * The downstream systems could email the user on a price decrease (hurry, the item is on sale!),
    or show a price change alert to the user when they next visit their cart, etc.

Note that the product service and shopping cart service are not part of this example project.
Example inbound events can be found in the [src/test/resources](./src/test/resources) directory.

![A diagramm showing the event flow for this example project](example-diagram.png "Illustration of the event flow for this example project")

## Running the isolated and integration tests 
To build and run the tests, use JDK 11 or greater (I'm using JDK 15).

Users running on Apple silicon should ensure that the file ~/.m2/settings.xml exists and contains the following:
```
<settings>
  <activeProfiles>
    <activeProfile>
      apple-silicon
    </activeProfile>
  </activeProfiles>
  <profiles>
    <profile>
      <id>apple-silicon</id>
      <properties>
        <os.detected.classifier>osx-x86_64</os.detected.classifier>
      </properties>
    </profile>
  </profiles>
</settings>
```


To compile the code and run the tests using the included Maven wrapper script, first see below about
building and installing Apache Flink Stateful Functions compatible with Flink 1.18, then do this:
```
./mvnw test
```

## Running the project via AWS Managed Flink

### Version compatibility between AWS Managed Flink and Stateful Functions

The latest release of Apache Flink Stateful Functions is 3.3, but its compiled and built 
to run with Flink 1.16.2.  AWS Managed Flink supports Flink versions 1.15 and 1.18.  So the first
step towards running via AWS Managed Flink is to create a version of the stateful functions library 
compatible with Flink 1.18.  The required changes are provided here: 
https://github.com/kellinwood/flink-statefun/pull/1/files.  
Clone that repo, checkout the `release-3.3-1.18`
branch, and build/install it locally via `mvn install`

### Build and package this project
```shell
mvn package
```

### Create an S3 bucket and upload this project's JAR file

To create the bucket, create a CloudFormation stack named `managed-flink-code-bucket` as defined [here](./managed-flink-poc-bucket.yaml),
and after that finishes, use the AWS CLI to upload the jar file:

```shell
export AWS_ACCOUNT_ID=516535517513 # Imagine Learning Sandbox account
aws s3 cp target/my-stateful-functions-embedded-java-3.3.0.jar \
          s3://managed-flink-code-bucket-codebucket-${AWS_ACCOUNT_ID}/
```

### Create the Kinesis streams, Managed Flink application, and related AWS Resources

Create a CloudFormation stack named `managed-flink-poc` as defined by the templates [here](./managed-flink-poc.yaml)

### Configure the Managed Flink application using the AWS Web Console

Visit `Managed Apache Flink` in the AWS web console and click through to the Flink application 
created via the CF stack above.  Note that the application is in the "ready" state and is not 
running yet.  

* Click the "Configure" button on the Flink application's detail page.
* Scoll down to the "Logging and monitoring" section.
* Click to turn on logging
* Click to use a custom log stream
* Click "Browse" to find the log stream
* Navigate through the log group named "managed-flink-poc-log-group..." and click the "Choose" button next to the 
  "managed-flink-poc-log-stream..." entry
* Under "Monitoring metrics level with CloudWatch" select "Operator"
* Scroll down to the bottom and click "Save changes"
* Once the changes have finished being saved, click the "Run" button to start the application.

Note that in many CloudFormation examples on how to deploy a Managed Flink application, the steps above 
are performed via API calls made by in-line lambdas defined in the CF template.  This is future work
for this example/demo project.

### Monitor the CloudWatch logging output

```shell
./poc-tail-logs.sh
```
This script will show all the log entries from the start of application launch, and will
wait for new entries to arrive and display them too.  The script will resume from where it 
left off if shut down via Ctrl-C.  To start from scratch, remove the `.cwlogs` directory.
### Send sample events to the ingress stream
```shell
./poc-send-events.sh
```

### Get and display the events published to the egress stream
```shell
./poc-get-events.sh
```

