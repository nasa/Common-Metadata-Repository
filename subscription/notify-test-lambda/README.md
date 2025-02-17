This lambda is a test lambda meant to be run in SIT and WL. 

A user creates an ingest granule subscription. An example follows:
{"Name": "Ingest-Subscription-Test-Sit-http",
 "Type": "granule",
 "SubscriberId": "eereiter",
 "CollectionConceptId": "C1200463968-CMR_ONLY",
 "EndPoint": "http://internal-cmr-services-sit-internal-only-2141831226.us-east-1.elb.amazonaws.com:80/notification/tester",
 "Mode": ["New", "Update", "Delete"],
 "Method": "ingest",
 "MetadataSpecification": {
   "URL": "https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1",
   "Name": "UMM-Sub",
   "Version": "1.1.1"
 }
}

Make sure the URL is the CMR internal load balancer followed by /notification/tester.

Ingest a granule. 

To verify that the notification is correct and that it was sent tunnel into the CMR internal load balancer
then issue a get request to the load balancer with the correct tunnel port number such as

curl http://localhost:8081/notification/tester

The contents of the notification will be sent back to the caller and can then be verified.

To send a test notification to the test tool send a post request. An example follows:

curl -XPOST -H "Content-Type: application/json" http://localhost:8081/notification/tester -d '{
  "Records": [
    {
      "EventSource": "aws:sns",
      "EventVersion": "1.0",
      "EventSubscriptionArn": "arn:aws:sns:us-east-1:832706493240:cmr-subscriptions-sit:1f071817-9e7b-450e-b5a5-606bef7c5b71",
      "Sns": {
        "Type": "Notification",
        "MessageId": "ed8c7ee0-c70a-5050-8ef9-1effe57d3dde",
        "TopicArn": "arn:aws:sns:us-east-1:832706493240:cmr-subscriptions-sit",
        "Subject": "testing again",
        "Message": "testing again",
        "Timestamp": "2025-02-06T20:48:55.564Z",
        "SignatureVersion": "1",
        "Signature": "iNvFhB7SvB2Um4xkJ4czhaDtQ9WMr3VWz91j9aBJclHQOSR1vznwdT6pJLJOj1fUA/C9JatNEnouUiPut+8DACZ8pVXfMw5bvHoyci63Y7z5gcmVIHpEMetn1Ms7SpgnuddrIoKpCN/tAQGrEfmzDlMbTXjRWrXpEs8xXsJ+Xq2Mp17FoMU00/JV/eAP8ktHX7TtS+dBGLsPd/QPY3eN5QYcmYAcoFd99Va4Qt6dMqOoH/fjIs1aCwnmD02MSnbjCWOgNSteZChnVAT/+l26krKoidHSop53guPt5KWNBGXTSy4Ouhq630qourN+AiVbKqzUtVypNTXas7iBEoT5Nw==",
        "SigningCertUrl": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-9c6465fa7f48f5cacd23014631ec1136.pem",
        "UnsubscribeUrl": "https://sns.us-east-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:us-east-1:832706493240:cmr-subscriptions-sit:1f071817-9e7b-450e-b5a5-606bef7c5b71",
        "MessageAttributes": {
          "endpoint": "http://host.docker.internal:5001/store"
        }
      }
    }
  ]
}'

