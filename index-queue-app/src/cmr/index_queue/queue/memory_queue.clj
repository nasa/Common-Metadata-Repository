(ns cmr.index-queue.queue.memory-queue
  "In-memory implementation of index-queue functionality"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
    				[cmr.common.config :as cfg]
        		[cmr.common.services.errors :as errors]))


