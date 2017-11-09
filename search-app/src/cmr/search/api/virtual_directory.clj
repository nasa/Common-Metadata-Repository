(ns cmr.search.api.virtual-directory
  "Functions for exposing the virtual directory api."
  (:require
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.search.api.core :as core-api]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.services.result-format-helper :as rfh]
   [cmr.search.services.virtual-directory :as virtual-directory-services]
   [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def virtual-directory-routes
  (context "/directories" []
    (context ["/:concept-id/:year/:month/:day/:hour"
              :concept-id #"[^\/]+" :year #"[^\/]+" :month #"[^\/]+" :day #"[^\/]+" :hour #".*$"]
      [concept-id year month day hour]
      ; (println "Year " year "Month " month "Day " day "Hour " hour)
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
                                               :day #".*$"] [concept-id year month day]
      ; (println "Year " year "Month " month "Day " day)
      (OPTIONS "/" req common-routes/options-response)
      (GET "/"
        {params :params headers :headers context :request-context}
        (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                {:year year
                                                                                 :month month
                                                                                 :day day})]
          (common-routes/search-response {:results results :result-format :json}))))
    (context ["/:concept-id/:year/:month" :concept-id #"[^\/]+" :year #"[^\/]+"
                                          :month #".*$"] [concept-id year month]
      (OPTIONS "/" req common-routes/options-response)
      (GET "/"
        {params :params headers :headers context :request-context}
        (let [results (virtual-directory-services/get-directories-by-collection context concept-id
                                                                                {:year year
                                                                                 :month month})]
          (common-routes/search-response {:results results :result-format :json}))))
    (context ["/:concept-id/:year" :concept-id #"[^\/]+" :year #".*$"] [concept-id year]
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
          (common-routes/search-response {:results results :result-format :json}))))))
