(ns cmr.common.util
  "Utility functions that might be useful throughout the CMR."
  (:require [cmr.common.log :refer (debug info warn error)]))

(defn sequence->fn
  [vals]
  "Creates a stateful function that returns individual values from the sequence. It returns the first
  value when called the first time, the second value on the second call and so on until the sequence
  is exhausted of values. Returns nil forever after that.

      user=> (def my-ints (sequence->fn [1 2 3]))
      user=> (my-ints)
      1
      user=> (my-ints)
      2
      user=> (my-ints)
      3
      user=> (my-ints)
      nil"
  (let [vals-atom (atom {:curr-val nil :next-vals (seq vals)})]
    (fn []
      (:curr-val (swap! vals-atom
                        (fn [{:keys [next-vals]}]
                          {:curr-val (first next-vals)
                           :next-vals (rest next-vals)}))))))

(defmacro future-with-logging
  "Creates a future that will log when a task starts and completes or if exceptions occur."
  [taskname & body ]
  `(future
    (info "Starting " ~taskname)
    (try
      ~@body

      (info ~taskname " completed without exception")

      (catch Throwable e#
        (error e# "Exception in " ~taskname)
        (throw e#))
      (finally
        (info ~taskname " complete.")))))

(defn parse-concept-id
  "Split a concept id into concept-type-prefix, sequence number, and provider id."
  [concept-id]
  (let [prefix (first concept-id)
        seq-num (re-find #"\d+" concept-id)
        provider-id (get (re-find #"\d+-(.*)" concept-id) 1)]
    {:concept-prefix prefix :sequence-number seq-num :provider-id provider-id}))