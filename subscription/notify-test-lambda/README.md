This lambda is a test lambda meant to be run in SIT and WL. 

A user creates an ingest granule subscription. An example follows:
{"Name": "Ingest-Subscription-Test-Sit-http",
 "Type": "granule",
 "SubscriberId": "user1",
 "CollectionConceptId": "C1200463968-CMR_ONLY",
 "EndPoint": "http://<the-internal-loadbalancer-url>/notification/tester",
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

To verify that the notification is correct and that it was sent through the tunnel into the CMR internal load balancer,
issue a get request to the load balancer with the correct tunnel port number such as

curl http://localhost:8081/notification/tester

The contents of the notification will be sent back to the caller and can then be verified.

To send a test notification to the test tool send a post request. An example follows:

curl -XPOST -H "Content-Type: application/json" http://localhost:8081/notification/tester -d '{
  "Records": [
    {
      "EventSource": "aws:sns",
      "EventVersion": "1.0",
      "EventSubscriptionArn": "arn:aws:sns:<region>:<account>:<SNS name>:<unique ID>",
      "Sns": {
        "Type": "Notification",
        "MessageId": "ed8c7ee0-c70a-5050-8ef9-1effe57d3dde",
        "TopicArn": "arn:aws:sns:<region>:<account>:<sns name>",
        "Subject": "testing again",
        "Message": "testing again",
        "Timestamp": "2025-02-06T20:48:55.564Z",
        "SignatureVersion": "1",
        "Signature": "iN...TXas7iBEoT5Nw==",
        "SigningCertUrl": "https://sns.<region>.amazonaws.com/SimpleNotificationService-9...6.pem",
        "UnsubscribeUrl": "https://sns.<region>.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=<subscription arn>",
        "MessageAttributes": {
          "endpoint": "URL"
        }
      }
    }
  ]
}'

