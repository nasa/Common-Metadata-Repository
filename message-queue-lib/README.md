# message-queue-lib

Library containing code to handle message queues within the CMR.  Implementation is tied to RabbitMQ.

## Handling Errors and Retries

* Errors (non-2xx HTTP responses) will result in retrying a fixed number of times with an exponentially increasing delay between retries. A different wait queue is used for each of the retries, each with its own retry interval. After the maximum number of retries has occurred the request will be logged with an error.
* Wait queues are configured to have no consumer but a TTL set for each message and the original queue set as its _dead letter queue_.  When a consumer on the main queue fails to process a request, the original message is acked then a copy with an appropriate TTL is added to the appropriate wait queue based on how many times it has already been tried. Since there is no consumer for the wait queue, eventually messages will time out and be added to the dead letter queue, the main queue, in this case. This pattern is described [here](zhttp://globaldev.co.uk/2014/07/back-off-and-retry-with-rabbitmq/).

## Publisher Backpressure

When publishers attempt to index a large number of concepts in a burst, RabbitMQ will provide backpressure using a limit on the amount of memory being used as [described here](http://www.rabbitmq.com/memory.html). Attempts to queue messages will block until the queue size decreases below the stated thresholds.

Note that RabbitMQ allows configuration of a maximum queue size, but it will just drop old messages, which is not the behavior we want.  As a result we use the memory limit.

## License

Copyright © 2021 NASA
