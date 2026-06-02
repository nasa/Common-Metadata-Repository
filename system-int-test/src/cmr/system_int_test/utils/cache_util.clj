(ns cmr.system-int-test.utils.cache-util
  "Verifies the cache api is working."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [cmr.system-int-test.system :as s]))

(defn list-caches-for-app
  "Gets a list of the caches for the given url."
  [url token]
  (let [response (client/request {:url url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]

    ;; Make sure the status returned is success
    (when (not= status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn list-cache-keys
  "Gets a list of the cache keys for the given cache at the given url."
  [url cache-name token]
  (let [full-url (str url "/" cache-name)
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the status returned is success
    (when (not= status 200)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn get-cache-value
  "Gets the value for a given key from the given cache. Checks that the returned status code
  matches the expected status code."
  [url cache-name cache-key token expected-status]
  (let [full-url (str url "/" cache-name "/" cache-key)
        response (client/request {:url full-url
                                  :method :get
                                  :query-params {:token token}
                                  :connection-manager (s/conn-mgr)
                                  :throw-exceptions false})
        status (:status response)]
    ;; Make sure the returned status matches the expected-status
    (when (not= status expected-status)
      (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
    (json/decode (:body response) true)))

(defn refresh-cache
  "Refreshes the cache for the given url."
 [url token]
 (let [response (client/request {:url url
                                 :method :post
                                 :query-params {:token token}
                                 :connection-manager (s/conn-mgr)
                                 :throw-exceptions false})
       status (:status response)]

    ;; Make sure the status returned is success
   (when (not= status 200)
     (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
   (json/decode (:body response) true)))
