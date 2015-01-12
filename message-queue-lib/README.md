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


### TODO

* Implement and test wait queue and error handling - 2 days

* Add background job to monitor Rabbit MQ message size - 1 day
	* File issue to add splunk alert to alert if message queue size grows too large

* Left to do for code
	* Refactoring existing code - 1 day
		* Log time from putting message on messge queue to getting response form elastic that it has been indexed. The time of queue insertion will need to be part of the message.
		* Log number of ingests and number of indexings performed.
	* Implement in-memory cue - 1/2 day
* Cluster/HA - 1/2 day
	* Design/Document
	* Configure rabbit mq with max memory size
	* File EI to deploy cluster



### Message Queue Problems

Possible issues:

  1. Rabbit MQ is unreachable
  2. Ingest is faster than indexing. This would cause the queue to fill up.
  3. A resource is unavailable like either the Indexer or Elasticsearch
  4. There are individual errors handling some of the messages.
     * This could happen if there's a bug in the indexer or if Ingest has a bug and allows invalid data.

#### Handling these problems:

##### Caught Error in the Indexer

If an error occurs in the indexer either because Elasticsearch is unavailable or an unexpected error occurs during indexing we will catch that error. The message will be placed on the Wait Queue (described elsewhere in README). We will use an exponential backoff to retry after a set period of time. After the message has been successfully queued on the wait queue the indexer should acknowledge the message.


The indexer will need the following configuration options:

  * CMR_INDEXER_RETRY_WAIT_TIME
  * CMR_INDEXER_NUMBER_OF_RETRIES

The wait time of the current retry will be CMR_INDEXER_RETRY_WAIT_TIME ^ num_retries. If the wait time is 5 minutes and it's the first time we're retrying we'll wait 5 minutes. The next time we would wait 25 minutes (5^2), then 125 minutes (5^3), and so on until we've exhausted the amount of time we'll wait.

Every time we fail the error should be logged. After retrying the maximum number of times we should log the failure.

##### Uncaught Error in the Indexer

An uncaught error such as indexer dying or running out of memory will be handled through non-acknowledgment of the message. Rabbit MQ will consider the messages as not having been processed and requeue it.

##### Message Queue size growing large

The message queue size could grow larger either because Ingest is faster than indexing or there is a problem in the indexer that causes it to requeue messages. We will have two ways to handle and prevent this problem, alerts and the maximum queue size.

###### Alerts

The indexer will have a background job that monitors the rabbit mq message queue size and logs it. If the message queue size exceeds some configured size (CMR_INDEXER_WARN_QUEUE_SIZE) we will log extra infomation that splunk can detect. We will add a splunk alert to look for the log mesage indicating the queue size has exceeded threshhold and email CMR Operations.

###### Maximum Queue Size

Rabbit MQ allows a maximum queue size but it will just drop old messages. It does support a limit on memory in Rabbit MQ. We'll use that configuration so that if Rabbit MQ memory has filled up with ~40% of memory with messages it will start blocking producers. This will cause ingest to wait until Rabbit MQ messages are processed.


##### Ingest unable to queue a message

This could happen either because it times out or Rabbit MQ is unavailable.  Ingest will treat this as an internal error and return the error to the provider. If this happens the data will still be left in Metadata DB and won't be indexed. It's possible this newer version of a granule could be returned from CMR but it is unlikely to happen.


TODOs from this decision that will need to be estimated

  * Implement and test wait queue and error handling
  * Configure rabbit mq with max memory size
  * Add background job to monitor Rabbit MQ message size
  * File issue to add splunk alert to alert if message queue size grows too large

## License

Copyright © 2015 NASA