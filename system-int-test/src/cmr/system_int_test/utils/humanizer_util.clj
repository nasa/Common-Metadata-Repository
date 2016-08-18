(ns cmr.system-int-test.utils.humanizer-util
  "This contains utilities for testing humanizer"
  (:require [cmr.transmit.humanizer :as h]
            [clojure.test :refer [is]]
            [cheshire.core :as json]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.common.concepts :as cc]
            [cmr.common-app.test.sample-humanizer :as sh]))

(defn grant-all-humanizer-fixture
  "A test fixture that grants all users the ability to create and modify humanizer"
  [f]
  (e/grant-all-admin (s/context))
  (f))

(defn make-humanizer
  "Makes a valid humanizer"
  ([]
   (make-humanizer nil))
  ([humanizer]
   (or humanizer sh/sample-humanizer)))

(defn- process-response
  "Returns the response in a map for easy testing"
  [{:keys [status body]}]
  (if (map? body)
    (assoc body :status status)
    {:status status
     :body body}))

(defn update-humanizer
  "Updates a humanizer."
  ([token humanizer]
   (update-humanizer token humanizer nil))
  ([token humanizer options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (h/update-humanizer (s/context) humanizer options)))))

(defn register-humanizer
  "Register the humanizer in CMR for testing"
  ([]
   (register-humanizer sh/sample-humanizer))
  ([humanizer]
   (let [token (e/login (s/context) "user2")]
     (update-humanizer token humanizer))))

(defn get-humanizer
  "Retrieves a humanizer by humanizer-key"
  []
  (process-response (h/get-humanizer (s/context) {:raw? true})))

(defn assert-humanizer-saved
  "Checks that a humanizer was persisted correctly in metadata db."
  [humanizer user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :humanizer
            :native-id cc/humanizer-native-id
            :provider-id "CMR"
            :format mt/json
            :metadata (json/generate-string humanizer)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

