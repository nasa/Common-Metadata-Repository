(ns cmr.search.api.virtual-directory
  "Functions for exposing the virtual directory api.
  Disclaimer: This namespace was added as a prototype and is not tested. If we want to expose this
  capability operationally we need to add tests and documentation."
  (:require
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.search.services.virtual-directory :as virtual-directory-services]
   [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(defconfig virtual-directory-routes-enabled
  "Whether to expose the virtual directory routes. Right now we are prototyping and may not want
  to expose publicly."
  {:default false
   :type Boolean})

(def virtual-directory-routes
  (if-not (virtual-directory-routes-enabled)
    ;; No routes
    (context "/" [])
    (context "/directories" []
      (context ["/:concept-id/:year/:month/:day/:hour"
                :concept-id #"[^\/]+" :year #"[^\/]+" :month #"[^\/]+" :day #"[^\/]+" :hour #".*$"]
        [concept-id year month day hour]
        (OPTIONS "/" req common-routes/options-response)
        (GET "/"
          {params :params headers :headers context :request-context}
          (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                  {:year year
                                                                                   :month month
                                                                                   :day day
                                                                                   :hour hour})]
            (common-routes/search-response {:results results :result-format :json}))))
      (context ["/:concept-id/:year/:month/:day" :concept-id #"[^\/]+" :year #"[^\/]+" :month #"[^\/]+"
                                                 :day #".*$"]
        [concept-id year month day]
        (OPTIONS "/" req common-routes/options-response)
        (GET "/"
          {params :params headers :headers context :request-context}
          (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                  {:year year
                                                                                   :month month
                                                                                   :day day})]
            (common-routes/search-response {:results results :result-format :json}))))
      (context ["/:concept-id/:year/:month" :concept-id #"[^\/]+" :year #"[^\/]+"
                                            :month #".*$"]
        [concept-id year month]
        (OPTIONS "/" req common-routes/options-response)
        (GET "/"
          {params :params headers :headers context :request-context}
          (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                  {:year year
                                                                                   :month month})]
            (common-routes/search-response {:results results :result-format :json}))))
      (context ["/:concept-id/:year" :concept-id #"[^\/]+" :year #".*$"]
        [concept-id year]
        (OPTIONS "/" req common-routes/options-response)
        (GET "/"
          {params :params headers :headers context :request-context}
          (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                  {:year year})]
            (common-routes/search-response {:results results :result-format :json}))))
      (context ["/:concept-id" :concept-id #".*$"] [concept-id]
        (OPTIONS "/" req common-routes/options-response)
        (GET "/"
          {params :params headers :headers context :request-context}
          (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                  {})]
            (common-routes/search-response {:results results :result-format :json})))))))
