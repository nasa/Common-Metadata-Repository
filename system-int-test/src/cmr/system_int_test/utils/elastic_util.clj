(ns cmr.system-int-test.utils.elastic-util
  "Helper to connect and take actions directly against elastic cluster for sys int tests"
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.url-helper :as url]))

(defn doc-present?
  "If doc is present return true, otherwise return false"
  [index-name doc-id elastic-name]
  (let [response (client/get
                   (format "%s/%s/_doc/_search?q=_id:%s" (url/elastic-root elastic-name) index-name doc-id)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (and (= 1 (get-in body [:hits :total :value]))
         (= doc-id (get-in body [:hits :hits 0 :_id])))))

(defn get-doc
  [index-name doc-id elastic-name]
  "Get a specific doc (granule) from a specified elastic cluster"
  (let [response (client/get
                   (format "%s/%s/_doc/%s" (url/elastic-root elastic-name) index-name doc-id)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    body))

(defn index-exists?
  "Check if index exists in elastic cluster"
  [index-name elastic-name]
  (let [response (client/get
                   (format "%s/%s" (url/elastic-root elastic-name) index-name)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)})]
    (= 200 (:status response))))