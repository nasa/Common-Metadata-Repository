(ns ^{:doc "provides index related utilities."}
  cmr.system-int-test.utils.index-util
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn refresh-elastic-index
  []
  (client/post (url/elastic-refresh-url) {:connection-manager (url/conn-mgr)}))

(defn bulk-index-provider
  "Call the bootstrap app to bulk index a provider."
  [provider-id]
  (let [response (client/request
                   {:method :post
                    :url (url/bulk-index-provider-url)
                    :body (format "{\"provider_id\": \"%s\"}" provider-id)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (url/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

