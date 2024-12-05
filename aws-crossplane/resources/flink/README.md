Two compositions are provided. The first, `flink-basic-comp.yaml` creates a Managed Flink instance and associated 
CloudWatch log group and stream.  This composition results in a Managed Flink instance in the 'Ready' state.  The second,
`flink-lambda-comp.yaml` goes further to also create a lambda that observes the Managed Flink instance and automatically
transitions it to the running state.