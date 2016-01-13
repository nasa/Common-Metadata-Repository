(ns cmr.common-app.test.client-util
  "Contains test utilities for testing clients"
  (:require [clj-http.client :as client]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]))

(defn app-running?
  "Returns true if the application appears to be running at the given port and url. The context
  should contain a system with an application context configured for the app."
  [context app-name]
  (try
    (client/options (conn/root-url (config/context->app-connection context app-name))
                    {:throw-exceptions false})
    true
    (catch java.net.ConnectException _
      false)))

(defn run-app-fixture
  "Test fixture that will automatically run the application if it's not detected as currently running.
  The context-or-fn should contain a system with an application context configured for the app or
  be a function that will return that."
  [context-or-fn app-name initial-system start-fn stop-fn]
  (fn [f]
    (let [context (if (fn? context-or-fn) (context-or-fn) context-or-fn)]
      (if (app-running? context app-name)
        (f)
        (let [system (start-fn initial-system)]
          (try
            (f)
            (finally
              (stop-fn system))))))))
