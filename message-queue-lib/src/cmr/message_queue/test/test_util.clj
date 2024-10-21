(ns cmr.message-queue.test.test-util
  "Namespace to test local sqs server"
  (:require
   ;[clj-http.lite.client :as client]
   [clj-http.client :as client]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue-server.embedded-sqs-server :as embedded-sqs-server]))

(defn server-running?
  "Check to see if the sqs server is already running."
  []
  (try
    (client/get (str (config/sqs-server-url) "/health"))
    true
    (catch Exception _
      false)))

(defn embedded-sqs-server-fixture
  "This function is a fixture that starts and stops the embedded sqs server
  for tests."
  [f]
  (if (server-running?)
    (f)
    (let [sqs-server (embedded-sqs-server/create-sqs-server)
          started-sqs-server (lifecycle/start sqs-server nil)]
      (f)
      (lifecycle/stop started-sqs-server nil))))
