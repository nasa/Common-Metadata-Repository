(ns cmr.common.channels
  (:require [taoensso.timbre :refer (debug info warn error)]
            [cmr.common.util :as util])
  (:import [java.util.concurrent
            ArrayBlockingQueue
            TimeUnit
            TimeoutException]))

(defprotocol Channel
  (push [channel i] "Puts the item on the channel. Blocks if the channels is full.")
  (pull [channel] "Pulls an item from the channel. Blocks until messages are available.")
  (close [channel] "Closes the channel.")
  (closed? [channel] "Returns true if the channel is closed."))


(defn ensure-open [chan]
  (when (closed? chan)
    (throw (IllegalStateException. "The channel is closed."))))

(defrecord ArrayBlockingQueueChannel
  [
   state
   queue]

  Channel

  (push
    [chan i]
    (ensure-open chan)
    (let [^ArrayBlockingQueue queue (:queue chan)]
      (when-not (.offer queue i 600000 TimeUnit/MILLISECONDS)
        (throw (TimeoutException. "Timed out waiting to push item on queue.")))))

  (pull
    [chan]
    (loop [item nil]
      (if item
        item
        (let [^ArrayBlockingQueue queue (:queue chan)]
          (when (or (not (empty? queue))
                    (not (closed? chan)))
            (recur (.poll queue 1000 TimeUnit/MILLISECONDS)))))))

  (closed?
    [chan]
    (= :closed @(:state chan)))

  (close
    [chan]
    (reset! (:state chan) :closed)))

(defn push-seq
  "Pushes the sequence of items one at a time onto the channel until the sequence ends."
  [chan items]
  (let [start (System/currentTimeMillis)]
    (loop [items items num-items 1]
      (when (and items (not (closed? chan)))

        (if-let [item (first items)]
          (push chan item)

          ;; Intermediate logging
          (when (= 0 (mod num-items 1000))
            (let [elapsed (-> (System/currentTimeMillis) (- start) (/ 1000.0))
                  rate (/ num-items elapsed)]
              (info num-items "messages pushed in" elapsed "s." rate " m/s."))))

        (recur (seq (rest items)) (inc num-items))))))


(defn consume-all
  "Consumes all the messages on the channel with the function f. Continues to wait until channel
  returns nil or stop-fn returns true for an item."
  [chan f]
  (let [start (System/currentTimeMillis)]
    (loop [item (pull chan) num-items 1]
      (when item

        ;; process the item
        (f item)

        ;; Intermediate logging. Commented out to avoid being too verbose.
        #_(when (= 0 (mod num-items 1000))
            (let [elapsed (-> (System/currentTimeMillis) (- start) (/ 1000.0))
                  rate (/ num-items elapsed)]
              (info num-items "messages consumed in" elapsed "s." rate " m/s.")))

        ;; Get another item
        (recur (pull chan) (inc num-items))))))

(defn channel
  "Creates a new channel of size n."
  [n]
  (map->ArrayBlockingQueueChannel
    {:state (atom :open)
     :queue (ArrayBlockingQueue. n)}))

(defn process-tasks
  "Processes a sequence of tasks on multiple threads.
  Takes a set of keyed arguments
  * tasks-fn - a function that returns a sequence of tasks to process
  * processor - A function that can process a single task
  * num-threads - The number of threads to use to process the tasks
  * task-name - The name of the task to use for logging"
  [& {:keys [tasks-fn processor num-threads task-name]}]
  (let [channel (channel 5)
        pusher (util/future-with-logging
                 (str task-name "Message Pusher")
                 (push-seq channel (tasks-fn))
                 ;; The readers can keep reading after it's closed but no new data can be written.
                 (close channel))
        readers (doall (for [i (range num-threads)]
                         (util/future-with-logging
                           (str task-name "Processor" i)
                           (consume-all channel processor))))]

    ;; Wait for pusher to finish
    @pusher
    ;; Wait for the readers to finish and return nil
    (dorun (map deref readers))
    nil))

(comment

  (def c (channel 5))

  (push c 1)
  (pull c)
  (count (:queue c))

  (future
    (push-seq c (range 100))
    (println "Finished pushing 100 items")
    (close c)
    (println "closed!"))


  (def f (future
           (consume-all c (fn [item]
                            (println "Pulled off " item)))
           (println "Done future")))

  )