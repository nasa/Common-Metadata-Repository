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
            [cmr.common-app.test.sample-humanizer :as sh]
            [cmr.system-int-test.utils.index-util :as index]))

(defn grant-all-humanizers-fixture
  "A test fixture that grants all users the ability to create and modify humanizers"
  [f]
  (e/grant-all-admin (s/context))
  (f))

(defn make-humanizers
  "Makes a valid humanizer"
  ([]
   (make-humanizers nil))
  ([humanizers]
   (or humanizers sh/sample-humanizers)))

(defn- process-response
  "Returns the response in a map for easy testing"
  [{:keys [status body]}]
  (if (and (map? body) (not (contains? body :humanizers)))
    (assoc body :status status)
    {:status status
     :body body}))

(defn update-humanizers
  "Updates the humanizers."
  ([token humanizers]
   (update-humanizers token humanizers nil))
  ([token humanizers options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (h/update-humanizers (s/context) humanizers options)))))

(defn save-humanizers
  "Save the given humanizers in CMR for testing"
  [humanizers]
  (let [token (e/login (s/context) "user2")]
    (update-humanizers token humanizers)))

(defn save-sample-humanizers-fixture
  "A test fixture that saves sample humanizers in CMR for testing"
  [f]
  (save-humanizers sh/sample-humanizers)
  (f))

(defn get-humanizers
  "Retrieves the humanizers"
  []
  (process-response (h/get-humanizers (s/context) {:raw? true})))

(defn assert-humanizers-saved
  "Checks that the humanizer instructions are persisted correctly as a humanizer concept in metadata db."
  [humanizers user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :humanizer
            :native-id cc/humanizer-native-id
            :provider-id "CMR"
            :format mt/json
            :metadata (json/generate-string humanizers)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))
