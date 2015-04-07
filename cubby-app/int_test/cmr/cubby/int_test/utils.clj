(ns cmr.cubby.int-test.utils
  "Provides integration tests utilities."
  (:require [clojure.test :refer :all]
            [cmr.transmit.config :as config]
            [cmr.transmit.cubby :as c]
            [cmr.cubby.system :as system]))

(def conn-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn conn-context
  "Retrieves a context map that contains a connection to the cubby app."
  []
  (when-not @conn-context-atom
    (reset! conn-context-atom {:system (config/system-with-connections {} [:cubby])}))
  @conn-context-atom)

(defn reset-fixture
  "Test fixture that resets the application before each test."
  [f]
  (c/reset (conn-context))
  (f))

(defn app-running?
  "Returns true if the cubby app is running."
  []
  (try
    (c/get-keys (conn-context))
    true
    (catch java.net.ConnectException _
      false)))

(defn run-app-fixture
  "Test fixture that will automatically run the application if it's not detected as currently running."
  [f]
  (if (app-running?)
    (f)
    (let [cubby-system (system/start (system/create-system))]
      (try
        (f)
        (finally
          (system/stop cubby-system))))))

(defn assert-value-saved-and-retrieved
  "Asserts that the given keyname and value can be set and retrieved."
  [key-name value]
  (is (= 200 (:status (c/set-value (conn-context) key-name value true))))
  (is (= {:status 200 :body value}
         (select-keys (c/get-value (conn-context) key-name true)
                      [:status :body]))))

(defn assert-keys
  "Asserts that the expected keys are retrieved from the cubby api."
  [expected-keys]
  (is (= (sort expected-keys)
         (sort (c/get-keys (conn-context))))))

