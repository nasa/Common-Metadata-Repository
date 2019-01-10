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
  ([as-data]
    (util/get-file (str const/providers-resource "/GES_DISC.json") as-data)))

(defn get-ges-disc-airx3std-collection
  ([]
    (get-ges-disc-airx3std-collection :xml))
  ([^Keyword file-type]
    (get-ges-disc-airx3std-collection file-type const/default-handler-key))
  ([^Keyword file-type as-data]
    (case file-type
      :umm-json (util/get-file
                 (str const/collections-resource "/GES_DISC/AIRX3STD_006.umm-json")
                 as-data)
      :json (util/get-file
             (str const/collections-resource "/GES_DISC/AIRX3STD_006.json")
             as-data)
      :xml (util/get-file
            (str const/collections-resource "/GES_DISC/AIRX3STD_006.xml")
            as-data))))

(defn get-ges-disc-airx3std-collection-metadata
  ([]
    (get-ges-disc-airx3std-collection-metadata :json))
  ([^Keyword file-type]
    (get-ges-disc-airx3std-collection-metadata
     file-type const/default-handler-key))
  ([^Keyword file-type as-data]
    (case file-type
      :umm-json (util/get-file
                 (str const/collections-resource
                      "/GES_DISC/AIRX3STD_006-metadata.umm-json")
                 as-data)
      :json (util/get-file
             (str const/collections-resource
                  "/GES_DISC/AIRX3STD_006-metadata.json")
             as-data)
      :xml (util/get-file
            (str const/collections-resource
                 "/GES_DISC/AIRX3STD_006-metadata.xml")
            as-data))))

(defn get-ges-disc-airx3std-opendap-service
  ([]
    (get-ges-disc-airx3std-opendap-service [:json :edn]))
  ([as-data]
    (util/get-file
     (str const/services-resource "/GES_DISC/AIRX3STD/OPeNDAP.umm-json")
     as-data)))

(defn get-ges-disc-airx3std-ch4-variable
  ([filename-part]
    (get-ges-disc-airx3std-ch4-variable filename-part :umm-json))
  ([filename-part ^Keyword file-type]
    (get-ges-disc-airx3std-ch4-variable filename-part file-type :data))
  ([filename-part ^Keyword file-type as-data]
    (util/get-file
      (format "%s/GES_DISC/AIRX3STD/CH4/CH4_%s.%s"
              const/variables-resource
              filename-part
              (name file-type))
      as-data)))

(defn get-ges-disc-airx3std-ch4-variables
  ([]
   (get-ges-disc-airx3std-ch4-variables :obj))
  ([as-data]
   (util/get-files
    (str const/variables-resource "/GES_DISC/AIRX3STD/CH4")
    as-data)))
