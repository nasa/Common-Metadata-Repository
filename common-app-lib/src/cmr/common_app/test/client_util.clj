(ns cmr.common-app.test.client-util
  "Contains test utilities for testing clients"
  (:require
   [clj-http.client :as client]
   [cmr.common-app.test.side-api :as side-api]
   [cmr.common.lifecycle :as l]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]))

(defn url-available?
  "Returns true if the given url can be connected to."
  [url]
  (try
    (client/options url
                    {:throw-exceptions false})
    true
    (catch java.net.ConnectException _
      false)))

(defn app-running?
  "Returns true if the application appears to be running at the given port and url. The context
  should contain a system with an application context configured for the app."
  [context app-name]
  (url-available? (conn/root-url (config/context->app-connection context app-name))))

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

(defn side-api-running?
  "Returns true if the side api is running on the configured port"
  []
  (url-available? (str "http://localhost:" (side-api/side-api-port))))

(defn side-api-fixture
  "Starts and stops the side api as a test fixture"
  [routes-fn system]
  (fn [f]
    (if (side-api-running?)
      (f)
      (let [server (side-api/create-side-server routes-fn)
            server (l/start server system)]
        (try
          (f)
          (finally
            (l/stop server system)))))))
