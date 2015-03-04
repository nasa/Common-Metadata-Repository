(ns cmr.system-int-test.utils.test-environment
  "Contains helper functions related to the test environment"
  ;; TODO - will this work? I'm not sure we can use dev-system here
  (require [cmr.dev-system.system :as dev-system]))

(defn real-database?
  "Returns true if running with a real database"
  []
  (= (dev-system/dev-system-db-type) :external))

(defn in-memory-database?
  "Returns true if running with a in-memory database"
  []
  (= (dev-system/dev-system-db-type) :in-memory))


(defn real-message-queue?
  "Returns true if running with a real database"
  []
  (= (dev-system/dev-system-message-queue-type) :external))


(defmacro only-with-real-database
  "Executes the body of the call if the test environment is running with the real Oracle DB."
  [& body]
  `(when (real-database?)
     ~@body))

(defmacro only-with-in-memory-database
  "Executes the body of the call if the test environment is running with the in memory database"
  [& body]
  `(when (in-memory-database?)
     ~@body))

(defmacro only-with-real-message-queue
  "Executes the body of the call if the test environment is running with the real RabbitMQ."
  [& body]
  `(when (real-message-queue?)
     ~@body))

(comment
  (real-database?)
  (real-message-queue?))