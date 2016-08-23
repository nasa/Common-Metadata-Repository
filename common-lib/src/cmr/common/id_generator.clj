(ns cmr.common.id-generator
  "The id generator allows generation of numeric ids that fit within a Java Long (63 bits). It's
  based on the described API for Twitter Snowflake (https://github.com/twitter/snowflake/). A single
  id contains values for the current time in milliseconds, a sequence number, and a worker identifier.

  time - The current time in milliseconds from the epoch as returned by System.currentTimeMillis.
  sequence - A number from 0 - 255 used if multiple ids are generated in a single millisecond.
  worker - A number from 0 - 255 identifying a single worker node. This should be unique in a cluster
  of machines. Ther should be one worker id per JVM process.

  The values map to Java Long in the following order:
  [time - 6 bytes] [sequence - 1 byte] [worker 1 byte]
  This allows the generated ids to be sorted in time order. Ids generated across workers will be
  sortable but not exactly ordered due to clock drift and if multiple ids are generated in a single
  ms."
  (:require [cmr.common.util :as util]))

(comment
  ;;Testing ID generation and view hex can be done like this
  (format "%016X" (id-from-state {:worker 1 :sequence 0 :time 1}))

  (let [some-ids (doall (take 10 (ids (new-id-state 1))))]
    (= some-ids (sort some-ids)))

  (clojure.pprint/pprint (take 7
                               (map id-from-state
                                    (iterate next-id-state (new-id-state 1))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants

(def
  ^{:doc "The length in bits of the time value in the id"
    :private true}
  time-bit-length 48)

(def
  ^{:doc "The position of the time value within a Java long from the right"
    :private true}
  time-pos (- 64 time-bit-length))

(def
  ^{:doc "The maximum value for a time."
    :private true}
  max-time 0x7fffffffffff)

(def
  ^{:doc "The length in bits of the sequence value in the id"
    :private true}
  sequence-bit-length 8)

(def
  ^{:doc "The position of the sequence value within a Java long from the right"
    :private true}
  sequence-pos (- time-pos sequence-bit-length))

(def
  ^{:doc "The maximum value for a sequence number"
    :private true}
  max-sequence 0xff)

(def
  ^{:doc "The maximum value for a worker id"
    :private true}
  max-worker 0xff)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pre and Post condition helpers

(defn- valid-worker?
  "Returns true if the worker value is valid"
  [worker]
  (and (>= worker 0)
       (<= worker max-worker)))

(defn- valid-sequence?
  "Returns true if the sequence value is valid."
  [sequence]
  (and (>= sequence 0)
       (<= sequence max-sequence)))

(defn- valid-time?
  "Returns true if the time value is valid."
  [time]
  (and (>= time 0)
       (<= time max-time)))

(defn- valid-id-state?
  "Returns true if this is a valid id state."
  [id-state]
  (and (valid-worker? (:worker id-state))
       (valid-sequence? (:sequence id-state))
       (valid-time? (:time id-state))))

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic current-time-millis
  "Gets the current time in milliseconds. Written to make code easier to test."
  []
  (System/currentTimeMillis))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API functions

(defn new-id-state
  "Returns a new id state for the given worker num."
  [worker-num]
  {:post [(valid-id-state? %)]}
  (if (> worker-num max-worker)
    (throw (IllegalArgumentException.
             (format "Worker value %d is greater than max %d."
                     worker-num
                     max-worker)))
    {:worker worker-num
     :sequence 0
     :time (current-time-millis)}))

(defn id-from-state
  "Returns the id given an id state. "
  [{:keys [worker sequence time] :as state}]
  {:pre [(valid-id-state? state)]}
  (+ (bit-shift-left time time-pos)
     (bit-shift-left sequence sequence-pos)
     worker))

(defn next-id-state
  "Steps to the next id state. If time has passed since the last id state generated it will
  reset the sequence value and use the current time. If the time ms value is the same it increments
  the sequence number. It will sleep for 1 ms if we've reached the maximum number of sequences in
  a single millisecond."
  [{:keys [worker sequence time] :as state}]
  {:pre [(valid-id-state? state)]
   :post [(valid-id-state? %)]}
  (let [curr-time (current-time-millis)]
    (if (= curr-time time)

      ;; Generating another id within a single millisecond.
      (if (= sequence max-sequence)
        (do
          ;; We've reached the maximum number of sequences we can generate in a millisecond.
          ;; Pause 1 millisecond then try generating an id again.
          (try
            (Thread/sleep 1)
            (catch InterruptedException e
              ;; If we're interrupted retry one more time. A secondary interruption will be thown
              ;; out of the function
              (Thread/sleep 1)))
          (next-id-state state))
        (update-in state [:sequence] inc))

      {:worker worker
       :sequence 0
       :time curr-time})))

(defn state-from-id
  "Extracts the worker, sequence, and time values from the id given."
  [id]
  {:post [(valid-id-state? %)]}
  (let [worker (bit-and id 0xff)
        sequence (bit-shift-right
                   (bit-and id 0xff00)
                   sequence-pos)
        time (bit-shift-right
               (bit-and id 0x7fffffffffff0000)
               time-pos)]
    {:worker worker
     :sequence sequence
     :time time}))

(defn ids
  "Returns an infinite lazy sequence of ids starting after the initial state."
  [initial-state]
  (map id-from-state (iterate next-id-state initial-state)))

(defn create-id-generator
  "Creates a stateful function that returns a different id everytime it is called. Use one of these
  for an entire app to guarantee unique ids within a single VM. Use a different worker id on different
  nodes to guarantee that they will generate unique ids."
  [worker-id]
  (util/sequence->fn (ids (new-id-state worker-id))))
