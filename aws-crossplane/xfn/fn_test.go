package main

import (
	"context"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/google/go-cmp/cmp/cmpopts"

	//	"google.golang.org/protobuf/testing/protocmp"
	"google.golang.org/protobuf/types/known/durationpb"

	"github.com/crossplane/crossplane-runtime/pkg/logging"
	fnv1 "github.com/crossplane/function-sdk-go/proto/v1"
	"github.com/crossplane/function-sdk-go/resource"
	"github.com/crossplane/function-sdk-go/response"
)

func TestRunFunction(t *testing.T) {

	type args struct {
		ctx context.Context
		req *fnv1.RunFunctionRequest
	}
	type want struct {
		rsp *fnv1.RunFunctionResponse
		err error
	}

	cases := map[string]struct {
		reason string
		args   args
		want   want
	}{
		"ResponseIsReturned": {
			reason: "The Function should return a fatal result if no input was specified",
			args: args{
				req: &fnv1.RunFunctionRequest{
					Meta: &fnv1.RequestMeta{Tag: "hello"},
					Input: resource.MustStructJSON(`{
						"apiVersion": "template.fn.crossplane.io/v1beta1",
						"kind": "Input"
					}`),
					Observed: &fnv1.State{
						Composite: &fnv1.Resource{
							Resource: resource.MustStructJSON(`{
								"apiVersion": "example.com/v1alpha1",
								"kind": "XManagedFlink",
								"metadata": {
  									"name": "flink-demo",
  									"namespace": "default"
								},
								"spec": {
									"resourceConfig": {
										"region": "us-east-2",
										"account": "000000000000",
										"name": "flink-test",
										"codeBucket": "flink-test-bucket",
										"codeFile": "flink-test-app.jar",
										"additionalPermissions": {
											"managedPolicyArns": [
												"arn:aws:iam::aws:policy/AmazonKinesisFullAccess"
											],
											"inlinePolicies": [
											    {
											        "name": "kinesis_policy",
          											"policy": "            {\n              \"Version\": \"2012-10-17\",\n              \"Statement\": [\n                {\n                  \"Effect\": \"Allow\",\n                  \"Resource\": [\n                    \"arn:aws:kinesis:us-east-2:516535517513:stream/flink-cp-demo-ingress\",\n                    \"arn:aws:kinesis:us-east-2:516535517513:stream/flink-cp-demo-egress\"\n                  ],\n                  \"Action\": [\n                    \"kinesis:DescribeStream\",\n                    \"kinesis:GetRecords\",\n                    \"kinesis:GetShardIterator\",\n                    \"kinesis:ListShards\",\n                    \"kinesis:PutRecord\"\n                  ]\n                }\n              ]\n            }\n"
						                        }
											]
										},
										"environmentProperties": [{
											"propertyGroup": [{
												"propertyGroupId": "StatefunApplicationProperties",
												"propertyMap": {
													"EVENTS_INGRESS_STREAM_DEFAULT": "flink-test-ingress",
													"EVENTS_EGRESS_STREAM_DEFAULT": "flink-demo-egress"
												}
											}]
										}]
									},
									"claimRef": {
										"apiVersion": "example.com/v1alpha1",
										"kind":       "ManagedFlink",
										"name":       "flink-test",
										"namespace":  "default"
									}
								}
							}`),
						},
					},
				},
			},
			want: want{
				rsp: &fnv1.RunFunctionResponse{
					Meta: &fnv1.ResponseMeta{Tag: "hello", Ttl: durationpb.New(response.DefaultTTL)},
					Results: []*fnv1.Result{
						{
							Severity: fnv1.Severity_SEVERITY_NORMAL,
							Message:  "response.Normal(rsp)",
							Target:   fnv1.Target_TARGET_COMPOSITE.Enum(),
						},
					},
					Conditions: []*fnv1.Condition{
						{
							Type:   "FunctionSuccess",
							Status: fnv1.Status_STATUS_CONDITION_TRUE,
							Reason: "Success",
							Target: fnv1.Target_TARGET_COMPOSITE_AND_CLAIM.Enum(),
						},
					},
				},
			},
		},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			f := &Function{log: logging.NewNopLogger()}
			_ /*rsp,*/, err := f.RunFunction(tc.args.ctx, tc.args.req)

			// if diff := cmp.Diff(tc.want.rsp, rsp, protocmp.Transform()); diff != "" {
			// 	t.Errorf("%s\nf.RunFunction(...): -want rsp, +got rsp:\n%s", tc.reason, diff)
			// }

			if diff := cmp.Diff(tc.want.err, err, cmpopts.EquateErrors()); diff != "" {
				t.Errorf("%s\nf.RunFunction(...): -want err, +got err:\n%s", tc.reason, diff)
			}
		})
	}
}
