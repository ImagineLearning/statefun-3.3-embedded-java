package main

import (
	"context"

	"github.com/crossplane/crossplane-runtime/pkg/errors"
	"github.com/crossplane/crossplane-runtime/pkg/fieldpath"
	"github.com/crossplane/crossplane-runtime/pkg/logging"
	fnv1 "github.com/crossplane/function-sdk-go/proto/v1"
	"github.com/crossplane/function-sdk-go/request"
	"github.com/crossplane/function-sdk-go/resource"
	"github.com/crossplane/function-sdk-go/response"
	"gopkg.in/yaml.v3"
)

// Function returns whatever response you ask it to.
type Function struct {
	fnv1.UnimplementedFunctionRunnerServiceServer

	log logging.Logger
}

// Helper function to create an array containing a map.  In Unstructured, the arrays must be of type []interface{},
// otherwise the Unstructured can't be coverted to Struct.
func arrayWithMap(value map[string]interface{}) []interface{} {
	result := make([]interface{}, 1)
	result[0] = value
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

const FLINK_APP_RESOURCE_NAME resource.Name = "flink-application"

// RunFunction runs the Function.
func (f *Function) RunFunction(_ context.Context, req *fnv1.RunFunctionRequest) (*fnv1.RunFunctionResponse, error) {
	f.log.Info("Running function", "tag", req.GetMeta().GetTag())

	rsp := response.To(req, response.DefaultTTL)

	reqYaml, err := yaml.Marshal(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot marshal req to YAML %T", req))
	}
	f.log.Info("Request", "YAML", string(reqYaml))

	desired, err := request.GetDesiredComposedResources(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot get desired composed resources in %T", req))
		return rsp, nil
	}

	oxr, err := request.GetObservedCompositeResource(req)
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get observed XR from %T", req))
		return rsp, nil
	}

	// Fetch required values from oxr.spec.resourceConfig
	region, _ := oxr.Resource.GetValue("spec.resourceConfig.region")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.region from %T", oxr))
		return rsp, nil
	}
	codeBucket, _ := oxr.Resource.GetValue("spec.resourceConfig.codeBucket")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.codeBucket from %T", oxr))
		return rsp, nil
	}
	codeFile, _ := oxr.Resource.GetValue("spec.resourceConfig.codeFile")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.codeFile from %T", oxr))
		return rsp, nil
	}
	environmentProperties, _ := oxr.Resource.GetValue("spec.resourceConfig.environmentProperties")
	if err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "Cannot get spec.resourceConfig.environmentProperties from %T", oxr))
		return rsp, nil
	}

	// Fetch optional values from oxr.spec.resourceConfig
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
						"applicationRestoreConfiguration": arrayWithMap(map[string]interface{}{
							"applicationRestoreType": applicationRestoreType,
							"snapshotName":           snapshotName,
						}),
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
				"name": "aws-provider",
			},
		},
	}

	// Workaround for https://github.com/crossplane-contrib/provider-upjet-aws/issues/1419.  Don't set startApplication in the MR until the
	// resource is READY, and continue to set it after it becomes RUNNING.
	composed, _ := request.GetObservedComposedResources(req)
	observedFlink, ok := composed[FLINK_APP_RESOURCE_NAME]
	if ok {
		v, err := observedFlink.Resource.GetValue("status.atProvider.status")
		if err == nil && (v == "READY" || v == "RUNNING") {
			flinkAppDesired.Resource.SetValue("spec.forProvider.startApplication", true)
		}
	}

	f.log.Info("response.Normal(rsp)")

	if err := response.SetDesiredComposedResources(rsp, desired); err != nil {
		response.Fatal(rsp, errors.Wrapf(err, "cannot set desired composed resources in %T", rsp))
		return rsp, err

	}
	response.Normal(rsp, "response.Normal(rsp)")
	f.log.Info("response.Normal(rsp)")

	// You can set a custom status condition on the claim. This allows you to
	// communicate with the user. See the link below for status condition
	// guidance.
	// https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#typical-status-properties
	response.ConditionTrue(rsp, "FunctionSuccess", "Success").
		TargetCompositeAndClaim()

	return rsp, nil
}
