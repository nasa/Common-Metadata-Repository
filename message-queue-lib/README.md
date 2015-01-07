# message-queue-lib

A Clojure library designed to ... well, that part is up to you.

## Usage

## Design Decisions

### Handling Errors and Retries

* Errors (non-2xx HTTP responses) will result in retrying a fixed number of times with
	an exponentitally increasing delay between retries. After the maximum number of retries
	has occurred the request will be logged with an error.
* Errors should be logged and a splunk alert should be created to catch these.

Rabbit MQ does not offer a retry delay feature, so we need to implement this ourselves. Two possible approaches:

	1. When a consumer fails to process a request, it can ack the original message then place a copy back on the queue, with an incremented retry count set. A future with a sleep could be used to create the delay without stalling the consumer.

	2. A separate "wait queue" can be used that has no consumer but a TTL set for each message
	and the original queue set as its _dead letter queue_.
	When a consumer on the main queue fails to process a request, the original message is acked
	then a copy with an appropraite TTL is added to the wait queue. Since there is no consumer
	for the wait queue, eventually messages will time out and be added to the dead letter queue
	the main queue, in this case. This pattern is described [here](zhttp://globaldev.co.uk/2014/07/back-off-and-retry-with-rabbitmq/).

	Of these two approaches, the second approach seems the safest, if perhaps a bit harder to
	understand at first. The first approach could create an arbitrary number of threads due to the
	use of futures, which is a problem.

### Publisher Backpressure

	When providers attempt to index a large number of concepts in a burst, Rabbit MQ will provide backpressure as [described here](http://www.rabbitmq.com/memory.html). Attempts to queue messags will block until the queue size decreases below the stated thresholds.

	Originally the idea was to set a fixed capacity for the queue, but this does Rabbit MQ does not behave as expected. When a fixed capacity queue is created, it will discard old messages (the front of the queue) when the capcity is exceeded. See https://www.rabbitmq.com/maxlength.html.


## License

Copyright © 2015 NASA