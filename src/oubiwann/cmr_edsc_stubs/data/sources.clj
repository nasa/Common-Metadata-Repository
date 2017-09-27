(ns oubiwann.cmr-edsc-stubs.data.sources
  (:require
   [clojure.java.io :as io]
   [oubiwann.cmr-edsc-stubs.util :as util]))

(def services-dir (io/resource "data/services"))
(def variables-dir (io/resource "data/variables"))

(defn get-ges-disc-provider
  []
  (io/resource "data/providers/GES_DISC.json"))

(defn get-ges-disc-airx3std-collection
  ([]
    (get-ges-disc-airx3std-collection :xml))
  ([file-type]
    (case file-type
      :json (io/resource "data/collections/GES_DISC/AIRX3STD_006.json")
      :xml (io/resource "data/collections/GES_DISC/AIRX3STD_006.xml"))))

(defn get-ges-disc-airx3std-opendap-service
  []
  (io/resource "resources/data/services/GES_DISC/AIRX3STD/OPeNDAP.json"))

(defn get-ges-disc-airx3std-ch4-variables
  []
  (util/get-files "resources/data/variables/GES_DISC/AIRX3STD/CH4"))
