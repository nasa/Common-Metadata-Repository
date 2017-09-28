(ns cmr-edsc-stubs.data.sources
  (:require
   [clojure.java.io :as io]
   [cmr-edsc-stubs.util :as util])
  (:import
   (clojure.lang Keyword)))

(def services-dir (util/get-file "data/services" :obj))
(def variables-dir (util/get-file "data/variables" :obj))

(defn get-ges-disc-provider
  ([]
    (get-ges-disc-provider :data))
  ([^Keyword as-data]
    (util/get-file "data/providers/GES_DISC.json" as-data)))

(defn get-ges-disc-airx3std-collection
  ([]
    (get-ges-disc-airx3std-collection :xml))
  ([^Keyword file-type]
    (case file-type
      :json (util/get-file "data/collections/GES_DISC/AIRX3STD_006.json")
      :xml (util/get-file "data/collections/GES_DISC/AIRX3STD_006.xml"))))

(defn get-ges-disc-airx3std-opendap-service
  []
  (util/get-file "data/services/GES_DISC/AIRX3STD/OPeNDAP.json"))

(defn get-ges-disc-airx3std-ch4-variable
  ([filename-part]
    (get-ges-disc-airx3std-ch4-variable filename-part :json))
  ([filename-part ^Keyword file-type]
    (get-ges-disc-airx3std-ch4-variable filename-part file-type :data))
  ([filename-part ^Keyword file-type ^Keyword as-data]
    (util/get-file
      (format "data/variables/GES_DISC/AIRX3STD/CH4/CH4_%s.%s"
              filename-part
              (name file-type))
      as-data)))

(defn get-ges-disc-airx3std-ch4-variables
  []
  (util/get-files "data/variables/GES_DISC/AIRX3STD/CH4"))
