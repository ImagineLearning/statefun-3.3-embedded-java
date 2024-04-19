Example Flink Stateful Functions Java Project


This is an example of a Flink Stateful Functions project implemented in Java which uses the statefun embedded sdk.

At the time of this writing the project serves to demonstrate testing in various ways:
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
  * The custom annotation `@StatefulFunctionTag` is used to find the stateful
    function implementations and bind them without having to write boilerplate code.
  * The router implementation relies on Spring to provide the list of 
    `com.example.stateful_functions.router.Forwarder` implementations.  
    Each forwarder is small piece of code that routes one or more specific event types
    to a stateful function.  To start routing a new event type, just implement another Forwarder.

Example events and functions are provided to implement some shopping
cart behaviors.  The project assumes the existence of upstream
microservices that send Product events (name,price,availability) and
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
  * The cart service sends a cart activity event to add a product to the cart.
  * Product events are routed to the product function
  * Cart activity events are routed to the cart function
  * The cart function subscribes to the product via internal messaging to the product function
  * Later, a product event is sent which increases the price of the product
  * The product function sends the latest product info to the subscribers (i.e, the cart function)
  * The cart function compares the two prices, and egresses a cart status event 
    with the price discrepancy for the product.
  * The downstream systems could email the user on a price decrease (hurry, the item is on sale!),
    or show a price change alert to the user when they next visit their cart, etc.

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

To compile the code and run the tests using the included Maven wrapper script...
```
./mvnw test
```

Build and run via `docker compose`:
```shell
# Build this project and create the jar file
./mvnw package

# Build the flink docker images, and re-run these if code changes have been made
docker compose build jobmanager
docker compose build taskmanager

# The statefun profile starts localstack, creates the kinesis streams, and starts the Flink jobmanager and taskmanager
docker compose --profile statefun up -d

# Optionally connect the IDE debugger to the taskmanager on localhost port 5066 at this point

# Send some events
docker compose --profile send-events up

# Get and display the events from the egress stream
# Note that some VPNs (i.e., ZScaler) can cause failures with 'yum'.  The workaround is to disconnect from the VPN first.
docker compose --profile get-egress-events up

# Shut everything down
docker compose --profile all down
```

