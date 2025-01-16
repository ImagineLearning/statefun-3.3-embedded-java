package main

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"

	"github.com/crossplane/crossplane-runtime/pkg/errors"
	"github.com/crossplane/crossplane-runtime/pkg/fieldpath"
	"github.com/crossplane/crossplane-runtime/pkg/logging"
	fncontext "github.com/crossplane/function-sdk-go/context"
	fnv1 "github.com/crossplane/function-sdk-go/proto/v1"
	"github.com/crossplane/function-sdk-go/request"
	"github.com/crossplane/function-sdk-go/resource"
	"github.com/crossplane/function-sdk-go/response"
)

// Function returns whatever response you ask it to.
type Function struct {
	fnv1.UnimplementedFunctionRunnerServiceServer

	log logging.Logger
}

func getEnvironmentConfig(req *fnv1.RunFunctionRequest) (*unstructured.Unstructured, error) {
	env := &unstructured.Unstructured{}
	if v, ok := request.GetContextKey(req, fncontext.KeyEnvironment); ok {
		if err := resource.AsObject(v.GetStructValue(), env); err != nil {
			return env, fmt.Errorf("cannot get Composition environment from %T context key %q", req, fncontext.KeyEnvironment)
		}
	}
	return env, nil
}

// Helper function to create an array containing a map.  In Unstructured, the arrays must be of type []interface{},
// otherwise the Unstructured can't be coverted to Struct.
func arrayWithMap(value map[string]interface{}) []interface{} {
	result := make([]interface{}, 1)
	result[0] = value
	return result
}

func arrayWithMaps(values []map[string]interface{}) []interface{} {
	result := make([]interface{}, len(values))
	for i, v := range values {
		result[i] = v
	}
	return result
}

// Get a value from the composite at the given path, and if not found return the defaultValue
func getValue(oxr *resource.Composite, path string, defaultValue any) (any, error) {
	v, err := oxr.Resource.GetValue(path)
	if err != nil {
		if fieldpath.IsNotFound(err) {
			return defaultValue, nil
		}
	}
	return v, err
}

func getArrayValue(oxr *resource.Composite, path string, defaultValue []interface{}) ([]interface{}, error) {
	v, err := oxr.Resource.GetValue(path)
	if err != nil {
		if fieldpath.IsNotFound(err) {
			return defaultValue, nil
		}
	}
	array, ok := v.([]interface{})
	if ok {
		return array, nil
	}
	return nil, errors.Errorf("Value at %s is not an array", path)
}

const FLINK_APP_RESOURCE_NAME resource.Name = "flink-application"
const LOG_GROUP_RESOURCE_NAME resource.Name = "flink-log-group"
const LOG_STREAM_RESOURCE_NAME resource.Name = "flink-log-stream"
const ROLE_RESOURCE_NAME resource.Name = "flink-role"

func RenderManagedFlinkResources(req *fnv1.RunFunctionRequest, rsp *fnv1.RunFunctionResponse, oxr *resource.Composite, log logging.Logger) (*fnv1.RunFunctionRequest, *fnv1.RunFunctionResponse) {

	desired, err := request.GetDesiredComposedResources(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot get desired composed resources in %T", req))
		return req, rsp
	}

	observed, err := request.GetObservedComposedResources(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot get observed composed resources in %T", req))
		return req, rsp
	}

	envConfig, err := getEnvironmentConfig(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot get env config in %T", rsp))
		return req, rsp
	}

	err = GenerateManagedFlink(rsp, desired, observed, oxr, log)
	if err != nil {
		response.Fatal(rsp, errors.Wrap(err, "failed to render ManagedFlink resource"))
		return req, rsp
	}

	err = GenerateRole(rsp, envConfig, desired, observed, oxr, log)
	if err != nil {
		response.Fatal(rsp, errors.Wrap(err, "failed to render Role resource"))
		return req, rsp
	}

	err = GenerateLogGroup(rsp, desired, observed, oxr, log)
	if err != nil {
		response.Fatal(rsp, errors.Wrap(err, "failed to render LogGroup resource"))
		return req, rsp
	}

	err = GenerateLogStream(rsp, desired, observed, oxr, log)
	if err != nil {
		response.Fatal(rsp, errors.Wrap(err, "failed to render LogStream resource"))
		return req, rsp
	}
	if err := response.SetDesiredComposedResources(rsp, desired); err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot set desired composed resources in %T", rsp))
		return req, rsp
	}

	oxr.Resource.SetValue("metadata.managedFields", nil)

	if err := response.SetDesiredCompositeResource(rsp, oxr); err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot set desired composite resource in %T", rsp))
		return req, rsp
	}

	return req, rsp
}

func GenerateManagedFlink(rsp *fnv1.RunFunctionResponse, desired map[resource.Name]*resource.DesiredComposed, observed map[resource.Name]resource.ObservedComposed, oxr *resource.Composite, log logging.Logger) error {
	// Fetch required values from oxr.spec.resourceConfig
	codeBucket, err := oxr.Resource.GetValue("spec.resourceConfig.codeBucket")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.codeBucket from %T", oxr))
		return err
	}
	codeFile, err := oxr.Resource.GetValue("spec.resourceConfig.codeFile")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.codeFile from %T", oxr))
		return err
	}
	environmentProperties, err := oxr.Resource.GetValue("spec.resourceConfig.environmentProperties")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.environmentProperties from %T", oxr))
		return err
	}

	// Fetch optional values from oxr.spec.resourceConfig
	region, _ := getValue(oxr, "spec.resourceConfig.region", "us-east-2")
	delayedStart, _ := getValue(oxr, "spec.resourceConfig.delayedStart", false)
	startApplication, _ := getValue(oxr, "spec.resourceConfig.startApplication", false)
	runtimeEnvironment, _ := getValue(oxr, "spec.resourceConfig.runtime", "FLINK-1_18")
	snapshotsEnabled, _ := getValue(oxr, "spec.resourceConfig.snapshotsEnabled", true)
	checkpointingEnabled, _ := getValue(oxr, "spec.resourceConfig.checkpointingEnabled", true)
	checkpointIntervalMillis, _ := getValue(oxr, "spec.resourceConfig.checkpointIntervalMillis", 300000) // 5 minutes by default
	logLevel, _ := getValue(oxr, "spec.resourceConfig.logLevel", "INFO")
	metricsLevel, _ := getValue(oxr, "spec.resourceConfig.metricsLevel", "TASK")
	autoScalingEnabled, _ := getValue(oxr, "spec.resourceConfig.autoScalingEnabled", false)
	parallelism, _ := getValue(oxr, "spec.resourceConfig.parallelism", 1)
	parallelismPerKpu, _ := getValue(oxr, "spec.resourceConfig.parallelismPerKpu", 1)
	applicationRestoreType, _ := getValue(oxr, "spec.resourceConfig.applicationRestoreType", "RESTORE_FROM_LATEST_SNAPSHOT")
	snapshotName, _ := getValue(oxr, "spec.resourceConfig.snapshotName", nil)
	allowNonRestoredState, _ := getValue(oxr, "spec.resourceConfig.allowNonRestoredState", false)

	flinkAppDesired := resource.NewDesiredComposed()
	desired[FLINK_APP_RESOURCE_NAME] = flinkAppDesired

	// Traverse environmentProperties and set AWS_REGION in all propertyMaps
	epa := environmentProperties.([]interface{})
	for _, v := range epa {
		epm := v.(map[string]interface{})
		pga := epm["propertyGroup"].([]interface{})
		for _, p := range pga {
			pgm := p.(map[string]interface{})
			propertyMap := pgm["propertyMap"].(map[string]interface{})
			propertyMap["AWS_REGION"] = region
		}
	}
	appRestoreConfig := map[string]interface{}{
		"applicationRestoreType": applicationRestoreType,
	}
	if snapshotName != nil {
		appRestoreConfig["snapshotName"] = snapshotName
	}

	flinkAppDesired.Resource.Object = map[string]interface{}{
		"apiVersion": "kinesisanalyticsv2.aws.upbound.io/v1beta1",
		"kind":       "Application",
		"metadata": map[string]interface{}{
			"name": oxr.Resource.GetClaimReference().Name,
		},
		"spec": map[string]interface{}{
			"deletionPolicy": "Delete", // "Orphan",
			"forProvider": map[string]interface{}{
				"region":             region,
				"runtimeEnvironment": runtimeEnvironment, // "FLINK-1_18",
				"applicationMode":    "STREAMING",
				"serviceExecutionRoleSelector": map[string]interface{}{
					"matchControllerRef": true,
				},
				"applicationConfiguration": arrayWithMap(map[string]interface{}{
					"applicationCodeConfiguration": arrayWithMap(map[string]interface{}{
						"codeContentType": "ZIPFILE",
						"codeContent": arrayWithMap(map[string]interface{}{
							"s3ContentLocation": arrayWithMap(map[string]interface{}{
								"fileKey": codeFile,
								"bucketArnSelector": map[string]interface{}{
									"matchLabels": map[string]interface{}{
										"crossplane.io/claim-name": codeBucket,
									},
								},
							}),
						}),
					}),
					"applicationSnapshotConfiguration": arrayWithMap(map[string]interface{}{
						"snapshotsEnabled": snapshotsEnabled,
					}),
					"environmentProperties": environmentProperties,
					"flinkApplicationConfiguration": arrayWithMap(map[string]interface{}{
						"checkpointConfiguration": arrayWithMap(map[string]interface{}{
							"checkpointInterval":   checkpointIntervalMillis,
							"checkpointingEnabled": checkpointingEnabled,
							"configurationType":    "CUSTOM",
						}),
						"monitoringConfiguration": arrayWithMap(map[string]interface{}{
							"logLevel":          logLevel,
							"metricsLevel":      metricsLevel,
							"configurationType": "CUSTOM",
						}),
						"parallelismConfiguration": arrayWithMap(map[string]interface{}{
							"autoScalingEnabled": autoScalingEnabled,
							"parallelism":        parallelism,
							"parallelismPerKpu":  parallelismPerKpu,
							"configurationType":  "CUSTOM",
						}),
					}),
					"runConfiguration": arrayWithMap(map[string]interface{}{
						"applicationRestoreConfiguration": arrayWithMap(appRestoreConfig),
						"flinkRunConfiguration": arrayWithMap(map[string]interface{}{
							"allowNonRestoredState": allowNonRestoredState,
						}),
					}),
				}),
				"cloudwatchLoggingOptions": arrayWithMap(map[string]interface{}{
					"logStreamArnSelector": map[string]interface{}{
						"matchControllerRef": true,
					},
				}),
			},
			"providerConfigRef": map[string]interface{}{
				"name": "provider-aws",
			},
		},
	}

	if delayedStart == true {
		// Workaround for https://github.com/crossplane-contrib/provider-upjet-aws/issues/1419.  Don't set startApplication in the MR until a few minutes
		// after the resource becomes READY
		observedFlink, ok := observed[FLINK_APP_RESOURCE_NAME]
		if ok {
			managedFlinkUpdateLoopWorkaround(flinkAppDesired, &observedFlink, oxr, log)
		}
	} else if startApplication == true {
		log.Info("Setting desiredFlink.spec.forProvider.startApplication=true")
		flinkAppDesired.Resource.SetValue("spec.forProvider.startApplication", true)
	}

	return nil // No error == Success
}

const WA1419_COUNTER_PATH string = "status.wa1419.reqCounter"
const WA1419_READYAT_PATH string = "status.wa1419.readyAt"
const WA1419_STARTAPP_PATH string = "status.wa1419.startApplication"

func managedFlinkUpdateLoopWorkaround(desiredFlink *resource.DesiredComposed, observedFlink *resource.ObservedComposed, oxr *resource.Composite, log logging.Logger) {
	// Workaround for https://github.com/crossplane-contrib/provider-upjet-aws/issues/1419.  Don't set startApplication in the MR until a few minutes
	// after the resource becomes READY
	v, err := observedFlink.Resource.GetValue("status.atProvider.status")
	if err != nil { // if atProvider.status is unavailable, then there is nothing to do
		log.Info("observed.status.atProvider.status is unavailable")
		return
	}
	log.Info("observed.status.atProvider", "status", v)

	var readyAt int64
	ra, _ := getValue(oxr, WA1419_READYAT_PATH, int64(0))
	raFloat64, ok := ra.(float64)
	if ok {
		readyAt = int64(raFloat64)
	} else {
		raInt64, ok := ra.(int64)
		if ok {
			readyAt = raInt64
		} else {
			readyAt = int64(0)
		}
	}

	if v == "READY" {
		log.Info(fmt.Sprintf("Got oxr.%s=%d", WA1419_READYAT_PATH, readyAt))
		if readyAt == 0 {
			readyAt = time.Now().UnixMilli()
			oxr.Resource.SetValue(WA1419_READYAT_PATH, readyAt)
			log.Info(fmt.Sprintf("Set oxr.%s=%d", WA1419_READYAT_PATH, readyAt))
		}
	}

	if readyAt > 0 {
		nowMillis := time.Now().UnixMilli()
		diffMillis := nowMillis - readyAt
		ds, _ := getValue(oxr, "spec.resourceConfig.delayStartBySeconds", 120) // 2 minutes by default
		log.Info("Timestamps", "nowMillis", nowMillis, "readyAtMillis", readyAt, "diff", diffMillis, "delayStartBySeconds", ds)
		delayStartBySeconds, ok := ds.(int64)
		if ok && diffMillis >= (delayStartBySeconds*time.Second.Milliseconds()) {
			log.Info("Setting desiredFlink.spec.forProvider.startApplication=true")
			desiredFlink.Resource.SetValue("spec.forProvider.startApplication", true)
		}
	}
}

func GenerateLogGroup(rsp *fnv1.RunFunctionResponse, desired map[resource.Name]*resource.DesiredComposed, observed map[resource.Name]resource.ObservedComposed, oxr *resource.Composite, log logging.Logger) error {
	logGroupDesired := resource.NewDesiredComposed()
	desired[LOG_GROUP_RESOURCE_NAME] = logGroupDesired

	// Fetch optional values from oxr.spec.resourceConfig
	region, _ := getValue(oxr, "spec.resourceConfig.region", "us-east-2")
	logGroupName, _ := getValue(oxr, "spec.resourceConfig.logGroupName", oxr.Resource.GetClaimReference().Name+"-log-group")
	retentionInDays, _ := getValue(oxr, "spec.resourceConfig.logRetentionInDays", 7)

	logGroupDesired.Resource.Object = map[string]interface{}{
		"apiVersion": "cloudwatchlogs.aws.upbound.io/v1beta1",
		"kind":       "Group",
		"metadata": map[string]interface{}{
			"name": logGroupName,
		},
		"spec": map[string]interface{}{
			"deletionPolicy": "Delete", // "Orphan",
			"forProvider": map[string]interface{}{
				"region":          region,
				"retentionInDays": retentionInDays,
			},
			"providerConfigRef": map[string]interface{}{
				"name": "provider-aws",
			},
		},
	}
	return nil // No error == Success
}

func GenerateLogStream(rsp *fnv1.RunFunctionResponse, desired map[resource.Name]*resource.DesiredComposed, observed map[resource.Name]resource.ObservedComposed, oxr *resource.Composite, log logging.Logger) error {
	logStreamDesired := resource.NewDesiredComposed()
	desired[LOG_STREAM_RESOURCE_NAME] = logStreamDesired

	// Fetch optional values from oxr.spec.resourceConfig
	region, _ := getValue(oxr, "spec.resourceConfig.region", "us-east-2")
	logStreamName, _ := getValue(oxr, "spec.resourceConfig.logGroupName", oxr.Resource.GetClaimReference().Name+"-log-stream")

	logStreamDesired.Resource.Object = map[string]interface{}{
		"apiVersion": "cloudwatchlogs.aws.upbound.io/v1beta1",
		"kind":       "Stream",
		"metadata": map[string]interface{}{
			"name": logStreamName,
		},
		"spec": map[string]interface{}{
			"deletionPolicy": "Delete", // "Orphan",
			"forProvider": map[string]interface{}{
				"region": region,
				"name":   logStreamName,
				"logGroupNameSelector": map[string]interface{}{
					"matchControllerRef": true,
				},
			},
			"providerConfigRef": map[string]interface{}{
				"name": "provider-aws",
			},
		},
	}
	return nil // No error == Success
}

func GenerateRole(rsp *fnv1.RunFunctionResponse, envConfig *unstructured.Unstructured, desired map[resource.Name]*resource.DesiredComposed,
	observed map[resource.Name]resource.ObservedComposed, oxr *resource.Composite, log logging.Logger) error {
	roleDesired := resource.NewDesiredComposed()
	desired[ROLE_RESOURCE_NAME] = roleDesired

	roleName := oxr.Resource.GetClaimReference().Name + "-role"

	// Fetch optional values from oxr.spec.resourceConfig
	region, _ := getValue(oxr, "spec.resourceConfig.region", "us-east-2")
	logGroupName, _ := getValue(oxr, "spec.resourceConfig.logGroupName", oxr.Resource.GetClaimReference().Name+"-log-group")
	additionalManagedPolicyArns, err := getArrayValue(oxr, "spec.resourceConfig.additionalPermissions.managedPolicyArns", []interface{}{})
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.additionalPermissions.managedPolicyArns from %T", oxr))
		return err
	}
	additionalInlinePolicies, err := getArrayValue(oxr, "spec.resourceConfig.additionalPermissions.inlinePolicies", []interface{}{})
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.additionalPermissions.inlinePolicies from %T", oxr))
		return err
	}

	assumeRolPolicy := `{
		"Version": "2012-10-17",
		"Statement": [
			{
			"Effect": "Allow",
			"Principal": {
				"Service": "kinesisanalytics.amazonaws.com"
			},
			"Action": "sts:AssumeRole"
			}
		]
		}	
	`

	managedPolicyArnCount := 2 + len(additionalManagedPolicyArns)

	managedPolicyArns := make([]interface{}, managedPolicyArnCount)
	managedPolicyArns[0] = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
	managedPolicyArns[1] = "arn:aws:iam::aws:policy/CloudWatchFullAccess"
	for i, v := range additionalManagedPolicyArns {
		managedPolicyArns[i+2] = v
	}

	inlinePolicyCount := 2 + len(additionalInlinePolicies)
	inlinePolicy := make([]map[string]interface{}, inlinePolicyCount)

	awsAccountID, ok := envConfig.Object["awsAccountID"]
	if !ok {
		if err != nil {
			response.Fatal(rsp, errors.Wrapf(err, "Cannot get awsAccountID from envConfig %T", envConfig))
			return err
		}
	}

	logGroupArn := fmt.Sprintf("arn:aws:logs:%s:%s:log-group:%s", region, awsAccountID, logGroupName)

	inlinePolicy[0] = map[string]interface{}{
		"name": "logs_policy",
		"policy": fmt.Sprintf(`{
	           "Version": "2012-10-17",
	           "Statement": [
	             {
	               "Effect": "Allow",
	               "Resource": [ "%s" ],
	               "Action": [
	                 "logs:DescribeLogGroups",
	                 "logs:DescribeLogStreams",
	                 "logs:PutLogEvents"
	               ]
	             }
	           ]
	         }`, logGroupArn),
	}
	inlinePolicy[1] = map[string]interface{}{
		"name": "metrics_policy",
		"policy": `{
	           "Version": "2012-10-17",
	           "Statement": [
	             {
	               "Effect": "Allow",
	               "Resource": "*",
	               "Action": [
	                 "cloudwatch:PutMetricData"
	               ]
	             }
	           ]
	         }`,
	}
	for i, v := range additionalInlinePolicies {
		m, ok := v.(map[string]interface{})
		if ok {
			inlinePolicy[i+2] = m
		} else {
			message := fmt.Sprintf("Entry at spec.resourceConfig.additionalPermissions.inlinePolicies[%d] is not a map", i)
			response.Fatal(rsp, errors.Wrap(err, message))
			return errors.New(message)
		}
	}

	roleDesired.Resource.Object = map[string]interface{}{
		"apiVersion": "iam.aws.upbound.io/v1beta1",
		"kind":       "Role",
		"metadata": map[string]interface{}{
			"name": roleName,
		},
		"spec": map[string]interface{}{
			"deletionPolicy": "Delete", // "Orphan",
			"forProvider": map[string]interface{}{
				"assumeRolePolicy":  assumeRolPolicy,
				"managedPolicyArns": managedPolicyArns,
				"inlinePolicy":      arrayWithMaps(inlinePolicy),
			},
			"providerConfigRef": map[string]interface{}{
				"name": "provider-aws",
			},
		},
	}
	return nil // No error == Success
}

// RunFunction runs the Function.
func (f *Function) RunFunction(_ context.Context, req *fnv1.RunFunctionRequest) (*fnv1.RunFunctionResponse, error) {
	rsp := response.To(req, response.DefaultTTL)

	/*
		reqYaml, err := yaml.Marshal(req)
		if err != nil {
			response.Fatal(rsp, errors.Wrapf(err, "cannot marshal req to YAML %T", req))
		}
		f.log.Info("Request", "YAML", string(reqYaml))
	*/

	oxr, err := request.GetObservedCompositeResource(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get observed XR from %T", req))
		return rsp, nil
	}

	metadataName, _ := getValue(oxr, "metadata.name", "nil")
	f.log.Info("Running function", "metadata.name", metadataName)

	desired, err := request.GetDesiredComposedResources(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot get desired composed resources in %T", req))
		return rsp, nil
	}

	RenderManagedFlinkResources(req, rsp, oxr, f.log)

	if err := response.SetDesiredComposedResources(rsp, desired); err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot set desired composed resources in %T", rsp))
		return rsp, err

	}
	response.Normalf(rsp, "Normal response for metadata.name=%s", metadataName)
	f.log.Info("Normal response", "metadata.name", metadataName)

	// You can set a custom status condition on the claim. This allows you to
	// communicate with the user. See the link below for status condition
	// guidance.
	// https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#typical-status-properties
	response.ConditionTrue(rsp, "FunctionSuccess", "Success").
		TargetCompositeAndClaim()

	return rsp, nil
}
