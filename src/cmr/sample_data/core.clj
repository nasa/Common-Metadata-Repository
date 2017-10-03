(ns cmr.sample-data.core
  (:require
   [clojure.java.io :as io]
   [cmr.sample-data.const :as const]
   [cmr.sample-data.util :as util])
  (:import
   (clojure.lang Keyword)))

(def services-dir (util/get-file const/services-resource :obj))
(def variables-dir (util/get-file const/variables-resource :obj))

(defn get-ges-disc-provider
  ([]
    (get-ges-disc-provider :data))
  ([^Keyword as-data]
    (util/get-file (str const/providers-resource "/GES_DISC.json") as-data)))

(defn get-ges-disc-airx3std-collection
  ([]
    (get-ges-disc-airx3std-collection :xml))
  ([^Keyword file-type]
    (get-ges-disc-airx3std-collection file-type const/default-as-data))
  ([^Keyword file-type as-data]
    (case file-type
      :json (util/get-file
             (str const/collections-resource "/GES_DISC/AIRX3STD_006.json")
             as-data)
      :xml (util/get-file
            (str const/collections-resource "/GES_DISC/AIRX3STD_006.xml")
            as-data))))

(defn get-ges-disc-airx3std-opendap-service
  ([]
   (get-ges-disc-airx3std-opendap-service const/default-as-data))
  ([as-data]
   (util/get-file
     (str const/services-resource "/GES_DISC/AIRX3STD/OPeNDAP.json")
     as-data)))

(defn get-ges-disc-airx3std-ch4-variable
  ([filename-part]
    (get-ges-disc-airx3std-ch4-variable filename-part :json))
  ([filename-part ^Keyword file-type]
    (get-ges-disc-airx3std-ch4-variable filename-part file-type :data))
  ([filename-part ^Keyword file-type ^Keyword as-data]
    (util/get-file
      (format "%s/GES_DISC/AIRX3STD/CH4/CH4_%s.%s"
              const/variables-resource
              filename-part
              (name file-type))
      as-data)))

(defn get-ges-disc-airx3std-ch4-variables
  ([]
   (get-ges-disc-airx3std-ch4-variables const/default-as-data))
  ([^Keyword as-data]
   (util/get-files
    (str const/variables-resource "/GES_DISC/AIRX3STD/CH4")
    as-data)))

(defn get-ges-disc-airx3std-opendap-service
  ([]
    (get-ges-disc-airx3std-opendap-service [:json :edn]))
  ([as-data]
    (util/get-file
     (str const/variables-resource "/GES_DISC/AIRX3STD/OPeNDAP.json")
     as-data)))
