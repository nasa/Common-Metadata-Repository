(ns cmr.virtual-product.config
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.message-queue.config :as mq-conf]
            [cmr.umm.granule :as umm-g]
            [clojure.string :as str]))

(defconfig virtual-products-enabled
  "Enables the updates of virtual products. If this is false every ingest event will be ignored
  by the virtual product application. If it is true ingest events will be processed and applied to
  the equivalent virtual products."
  {:default true
   :type Boolean})

(defconfig virtual-product-queue-name
  "The queue containing ingest events for the virtual product app."
  {:default "cmr_virtual_product.queue"})

(defconfig ingest-exchange-name
  "The ingest exchange to which messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the virtual-product application."
  []
  (assoc (mq-conf/default-config)
         :queues [(virtual-product-queue-name)]
         :exchanges [(ingest-exchange-name)]
         :queues-to-exchanges {(virtual-product-queue-name)
                               (ingest-exchange-name)}))

(defconfig virtual-product-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def source-to-virtual-product-config
  "A map of source collection provider id and entry titles to virtual product configs"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   {:source-short-name "AST_L1A"
    :virtual-collections [{:entry-title "ASTER On-Demand L2 Surface Emissivity"
                           :short-name "AST_05"}
                          {:entry-title "ASTER On-Demand L2 Surface Reflectance"
                           :short-name "AST_07"}
                          {:entry-title "ASTER On-Demand L2 Surface Reflectance VNIR and SWIR Crosstalk-Corrected"
                           :short-name "AST_07XT"}
                          {:entry-title "ASTER On-Demand L2 Surface Kinetic Temperature"
                           :short-name "AST_08"}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance SWIR and VNIR"
                           :short-name "AST_09"}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance VNIR and SWIR Crosstalk-Corrected"
                           :short-name "AST_09XT"}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance TIR"
                           :short-name "AST_09T"}
                          {:entry-title "ASTER On-Demand L3 Digital Elevation Model, GeoTIF Format"
                           :short-name "AST14DEM"}
                          {:entry-title "ASTER On-Demand L3 Orthorectified Images, GeoTIF Format"
                           :short-name "AST14OTH"}
                          {:entry-title "ASTER On-Demand L3 DEM and Orthorectified Images, GeoTIF Format"
                           :short-name "AST14DMO"}]}
   ["GSFCS4PA" "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003"]
   {:source-short-name "OMUVBd"
    :virtual-collections [{:entry-title "OMI/Aura Surface UVB UV Index, Erythemal Dose, and Erythemal Dose Rate Daily L3 Global 1.0x1.0 deg Grid V003"
                           :short-name "OMUVBd_ErythermalUV"}]}})

(def virtual-product-to-source-config
  "A map derived from the map source-to-virtual-product-config. This map consists of keys which are
  a combination of provider id and entry title for each virtual product and values which are made up
  of short name, source entry title and source short name for each of the keys"
  (into {}
        (for [[[provider-id source-entry-title] vp-config] source-to-virtual-product-config
              :let [{:keys [source-short-name virtual-collections]} vp-config]
              virtual-collection virtual-collections]
          [[provider-id (:entry-title virtual-collection)]
           {:short-name (:short-name virtual-collection)
            :source-entry-title source-entry-title
            :source-short-name source-short-name}])))

(def sample-source-granule-urs
  "This contains a map of source collection provider id and entry title tuples to sample granule
  urs. This is included both for testing and documentation of what the sample URs look like"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   ["SC:AST_L1A.003:2006227720"
    "SC:AST_L1A.003:2006227722"
    "SC:AST_L1A.003:2006227710"]
   ["GSFCS4PA" "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003"]
   ["OMUVBd.003:OMI-Aura_L3-OMUVBd_2004m1001_v003-2013m0314t081851.he5"
    "OMUVBd.003:OMI-Aura_L3-OMUVBd_2004m1012_v003-2014m0117t110510.he5"
    "OMUVBd.003:OMI-Aura_L3-OMUVBd_2005m0305_v003-2014m0117t163602.he5"]})

(defmulti generate-granule-ur
  "Generates a new granule ur for the virtual collection"
  (fn [provider-id source-short-name virtual-short-name granule-ur]
    [provider-id source-short-name]))

;; AST_L1A granule urs look like this "SC:AST_L1A.003:2006227720". We generate them by using the
;; short name of the virtual collection instead of the source.
(defmethod generate-granule-ur ["LPDAAC_ECS" "AST_L1A"]
  [provider-id source-short-name virtual-short-name granule-ur]
  (str/replace granule-ur source-short-name virtual-short-name))

(defmethod generate-granule-ur ["GSFCS4PA" "OMUVBd"]
  [provider-id source-short-name virtual-short-name granule-ur]
  (str/replace-first granule-ur source-short-name virtual-short-name))

;; The granule urs of granules in the virtual collection based on AST_L1A is a simple
;; transformation of the granule urs of the corresponding source granules and its inverse is trivial.
;; It is possible that future collections use a different scheme to generate virtual granule urs. In
;; those cases it might not even be possible to compute the inverse. We might take different approach
;; to find source granule ur from virtual granule ur to accommodate those cases.
;; We could, for example, add source granule ur as an additional attribute in the virtual granule
;; metadata which will be looked up instead of computing on the fly.
(defmulti compute-source-granule-ur
  "Compute source granule ur from the virtual granule ur. This function should be the inverse
  of generate-granule-ur."
  (fn [provider-id source-short-name virtual-short-name virtual-granule-ur]
    [provider-id source-short-name]))

(defmethod compute-source-granule-ur ["LPDAAC_ECS" "AST_L1A"]
  [provider-id source-short-name virtual-short-name virtual-granule-ur]
  (str/replace virtual-granule-ur virtual-short-name source-short-name))


(defmethod compute-source-granule-ur ["GSFCS4PA" "OMUVBd"]
  [provider-id source-short-name virtual-short-name virtual-granule-ur]
  (str/replace-first virtual-granule-ur virtual-short-name source-short-name))

(def source-granule-ur-additional-attr-name
  "The name of the additional attribute used to store the granule-ur of the source granule"
  "source granule-ur")

(defn- update-core-fields
  "Update the core set of fields in the source granule umm to create the virtual granule umm. These
  updates are common across all the granules in all the virtual collections. The remaining fields
  are inherited by the virtual granule automatically from the source granule unless overridden by
  the dispatch function update-virtual-granule-umm"
  [src-granule-umm virt-granule-ur virtual-coll]
  (let [src-granule-ur (:granule-ur src-granule-umm)]
    (-> src-granule-umm
        (assoc :granule-ur virt-granule-ur
               :collection-ref (umm-g/map->CollectionRef (select-keys virtual-coll [:entry-title])))
        ;; TODO: All virtual granules will have an additional attribute to store the granule-ur of
        ;; the source granule. Since each additional attribute in a granule must reference an
        ;; additional attribute with the same name in the corresponding collection, it is assumed
        ;; that all the virtual collections ingested will have this attribute. The ingest of virtual
        ;; granules will fail if the collection does not have the attribute.
        ;; Currently, there is no way to enforce this in the code. Ingest of a virtual collection is
        ;; no way different from ingest of a regular collection. We might want a way for ingest-app
        ;; to distinguish a virtual collection from regular collection to enforce this.
        (update-in [:product-specific-attributes] conj
                   (umm-g/map->ProductSpecificAttributeRef
                     {:name source-granule-ur-additional-attr-name
                      :values [src-granule-ur]})))))

;; This is the main dispatching function used for updating virtual granules from the source
;; granules based on the source collection on which a virtual granule's collection is based. We
;; might want to move the functionality encapsulated within each of the dispatch function to its own
;; file while leaving the default here.
(defmulti update-virtual-granule-umm
  "Dispatch function to update virtual granule umm based on source granule umm. All the non-core
  attributes of a virtual granule are inherited from source granule by default. This dispatch
  function is used for custom update of the virtual granule umm based on source granule umm."
  (fn [provider-id source-short-name source-umm virtual-umm]
    [provider-id source-short-name]))

;; Default is to not do any update
(defmethod update-virtual-granule-umm :default
  [provider-id source-short-name source-umm virtual-umm]
  virtual-umm)

(defn- update-online-access-url
  "Update online-access-url of OMI/AURA virtual-collection to use an OpenDAP url."
  [related-urls src-granule-ur]
  (let [fname (second (str/split src-granule-ur #":"))]
    (seq (for [related-url related-urls
               :let [url (:url related-url)
                     re (java.util.regex.Pattern/compile (str "(.*/data/s4pa/.*)(" fname ")$"))
                     matches (re-matches re url)]]
           (if matches
             (assoc related-url :url (str (str/replace (second matches)
                                                       "/data/s4pa/" "/opendap/HDF-EOS5")
                                          (nth matches 2)
                                          ".nc?ErythemalDailyDose,ErythemalDoseRate,UVindex"))
             related-url)))))

(defmethod update-virtual-granule-umm ["GSFCS4PA" "OMUVBd"]
  [provider-id source-short-name source-umm virtual-umm]
  (update-in virtual-umm [:related-urls] update-online-access-url (:granule-ur source-umm)))

(defn generate-virtual-granule-umm
  "Generate the virtual granule umm based on source granule umm"
  [provider-id source-short-name source-umm virtual-coll virtual-granule-ur]
  (let [virtual-umm (update-core-fields source-umm virtual-granule-ur virtual-coll)]
    (update-virtual-granule-umm provider-id source-short-name
                                source-umm virtual-umm)))

