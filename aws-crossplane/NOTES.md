
I initially put the stream ARN values in the environment section of (the managed flink claim)[./claims/mananged-flink-claim.yaml].
Just the plain stream names are required, however after updating the values in the claim and applying the change, I see this
error in the output of `kubectl describe application.kinesisanalyticsv2.aws.upbound.io/flink-demo-application`...

```
Warning  CannotUpdateExternalResource     4m19s (x14 over 6m31s)  managed/kinesisanalyticsv2.aws.upbound.io/v1beta1, kind=application  
(combined from similar events): async update failed: failed to update the resource: [{0 updating Kinesis Analytics v2 Application 
(arn:aws:kinesisanalytics:us-east-2:516535517513:application/flink-demo-application): operation error Kinesis Analytics V2: 
UpdateApplication, https response error StatusCode: 400, RequestID: 39586af4-c1cc-4515-b818-c86f8f176671, 
InvalidApplicationConfigurationException: Failed to take snapshot for the application flink-demo-application at this moment. 
The application is currently experiencing downtime. Please check the application's CloudWatch metrics or CloudWatch 
logs for any possible errors and retry the request. You can also retry the request after disabling the snapshots in 
the Kinesis Data Analytics console or by updating the ApplicationSnapshotConfiguration through the AWS SDK.  []}]
```

It appears that the snapshot issue is preventing the update that would fix the snapshot issue :(


I then tried to delete the claim and re-apply it as soon as the managed resources disappeared and the Flink app no longer 
showed in the AWS console, but the new app got stuck on this:

```
Warning  CannotCreateExternalResource     51s (x39 over 4m41s)   managed/kinesisanalyticsv2.aws.upbound.io/v1beta1, kind=application  
(combined from similar events): async create failed: failed to create the resource: [{0 creating Kinesis Analytics v2 Application 
(flink-demo-application): operation error Kinesis Analytics V2: CreateApplication, https response error StatusCode: 400, 
RequestID: 64366786-9f40-440f-8fcd-c3376f0cc619, ConcurrentModificationException: Tags are already registered for this 
resource ARN: arn:aws:kinesisanalytics:us-east-2:516535517513:application/flink-demo-application, please retry later. 
Or you can create without tags and then add tags using TagResource API after successful resource creation.  []}]
```

Third try after waiting longer between delete and apply...

```
Normal   UpdatedExternalResource          99s (x2 over 5m18s)    managed/kinesisanalyticsv2.aws.upbound.io/v1beta1, kind=application  Successfully requested update of external resource
```

But in the AWS Console, the app seems stuck with the 'Updating' status.  OK, waited a bit and it's now 'Running', except...

```
{
    "applicationARN": "arn:aws:kinesisanalytics:us-east-2:516535517513:application/flink-demo-application",
    "applicationVersionId": "3",
    "locationInformation": "org.apache.hadoop.fs.s3a.impl.MultiObjectDeleteSupport.translateDeleteException(MultiObjectDeleteSupport.java:107)",
    "logger": "org.apache.hadoop.fs.s3a.impl.MultiObjectDeleteSupport",
    "message": "AccessDenied: b97af75851aadd301cb2f64ad11c0ef0-516535517513-1733357577755/: User: arn:aws:sts::695788120607:assumed-role/AWSKinesisAnalyticsKubern-S3CustomerAppStateAccess-LZ083MW7K490/FlinkApplicationStateSession is not authorized to perform: s3:DeleteObject on resource: \"arn:aws:s3:::cc75a9b61f353980b2f0360aaee434149a950968/b97af75851aadd301cb2f64ad11c0ef0-516535517513-1733357577755/\" because no session policy allows the s3:DeleteObject action\n",
    "messageSchemaVersion": "1",
    "messageType": "WARN",
    "threadName": "s3a-transfer-cc75a9b61f353980b2f0360aaee434149a950968-unbounded-pool2-t16"
}
```

And were back on 'Updating' status w/o doing anything except to go look at the logs, where I saw an error about not 
having permissions to delete objects from S3, and while I was typing this it went back to 'Running' status.
