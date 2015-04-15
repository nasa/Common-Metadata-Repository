(ns cmr.cubby.int-test.utils
  "Provides integration tests utilities."
  (:require [clojure.test :as ct :refer [is]]
            [cmr.transmit.config :as config]
            [cmr.transmit.cubby :as c]
            [cmr.cubby.system :as system]
            [cmr.mock-echo.system :as mock-echo-system]
            [cmr.mock-echo.client.mock-echo-client :as mock-echo-client]
            [cmr.elastic-utils.test-util :as elastic-test-util]
            [cmr.common-app.test.client-util :as common-client-test-util]
            [clj-http.client :as client]))

(def conn-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn conn-context
  "Retrieves a context map that contains a connection to the cubby app."
  []
  (when-not @conn-context-atom
    (reset! conn-context-atom {:system (config/system-with-connections {} [:cubby :echo-rest])}))
  @conn-context-atom)

(defn int-test-fixtures
  "Returns test fixtures for starting the cubby application and its external dependencies."
  []
  (ct/join-fixtures
    [elastic-test-util/run-elastic-fixture
     (common-client-test-util/run-app-fixture
       (conn-context)
       :cubby
       (system/create-system)
       system/start
       system/stop)
     (common-client-test-util/run-app-fixture
       (conn-context)
       :echo-rest
       (mock-echo-system/create-system)
       mock-echo-system/start
       mock-echo-system/stop)]))

(defn reset-fixture
  "Test fixture that resets the application before each test."
  [f]
  (mock-echo-client/reset (conn-context))
  (c/reset (conn-context))
  (f))

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

