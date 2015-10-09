(ns cmr.virtual-product.config
  "Namespace to hold configuration rules for creating virtual granules from source granules."
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.message-queue.config :as mq-conf]
            [cmr.umm.granule :as umm-g]
            [clojure.string :as str])
  (:import java.util.regex.Pattern))

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

(defconfig virtual-product-provider-aliases
  "For each provider-id for which a virtual product is configured, define a set of provider-ids
  which have the same virtual product configuration as the original."
  {:default {"LPDAAC_ECS"  #{}
             "GSFCS4PA" #{}}
   :type :edn})

(defn provider-alias->provider-id
  "Get the provider-id given the alias. If the alias is provider-id itself, provider-id is returned. nil is returned if it is not an alias for any provider-id"
  [alias]
  (let [provider-aliases (virtual-product-provider-aliases)]
    ;; Check if alias is one of the provider ids defined in provider aliases configuration
    (if (contains? provider-aliases alias)
      alias
      (some (fn [[provider-id aliases]]
              (when (contains? aliases alias) provider-id)) provider-aliases))))

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the virtual-product application."
  []
  (assoc (mq-conf/default-config)
         :queues [(virtual-product-queue-name)]
         :exchanges [(ingest-exchange-name)]
         :queues-to-exchanges {(virtual-product-queue-name)
                               [(ingest-exchange-name)]}))

(defconfig virtual-product-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defn- match-all
  "Returns a function which checks if the granule umm matches with each of the matchers given"
  [& matchers]
  (fn [granule]
    (every? identity (map #(% granule) matchers))))

(defn- matches-value
  "A matcher which checks if the value in the granule umm given by ks matches value"
  [ks value]
  (fn [granule]
    (= value (get-in granule ks))))

(defn- matches-on-psa
  "A matcher which checks the existence of a psa in the granule umm whose name is
  psa-name and whose values have value as one of the memebers"
  [psa-name value]
  (fn [granule]
    (some #(and (= (:name %) psa-name) (some #{value} (:values %)))
          (:product-specific-attributes granule))))

(def day-granule? (matches-value [:data-granule :day-night] "DAY"))
(def tir-mode? (matches-on-psa "TIR_ObservationMode" "ON"))
(def swir-mode? (matches-on-psa "SWIR_ObservationMode" "ON"))
(def vnir1-mode? (matches-on-psa "VNIR1_ObservationMode" "ON"))
(def vnir2-mode? (matches-on-psa "VNIR2_ObservationMode" "ON"))

(def source-to-virtual-product-config
  "A map of source collection provider id and entry titles to virtual product configs"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   {:short-name "AST_L1A"
    :virtual-collections [{:entry-title "ASTER On-Demand L2 Surface Emissivity"
                           :short-name "AST_05"
                           :matcher tir-mode?}
                          {:entry-title "ASTER On-Demand L2 Surface Reflectance"
                           :short-name "AST_07"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L2 Surface Reflectance VNIR and SWIR Crosstalk-Corrected"
                           :short-name "AST_07XT"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L2 Surface Kinetic Temperature"
                           :short-name "AST_08"
                           :matcher tir-mode?}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance SWIR and VNIR"
                           :short-name "AST_09"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance VNIR and SWIR Crosstalk-Corrected"
                           :short-name "AST_09XT"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L2 Surface Radiance TIR"
                           :short-name "AST_09T"
                           :matcher tir-mode?}
                          {:entry-title "ASTER On-Demand L3 Digital Elevation Model, GeoTIF Format"
                           :short-name "AST14DEM"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L3 Orthorectified Images, GeoTIF Format"
                           :short-name "AST14OTH"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER On-Demand L3 DEM and Orthorectified Images, GeoTIF Format"
                           :short-name "AST14DMO"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}]}
   ["GSFCS4PA" "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003"]
   {:short-name "OMUVBd"
    :virtual-collections [{:entry-title "OMI/Aura Surface UVB UV Index, Erythemal Dose, and Erythemal Dose Rate Daily L3 Global 1.0x1.0 deg Grid V003"
                           :short-name "OMUVBd_ErythemalUV"}]}
   ["GSFCS4PA" "OMI/Aura TOMS-Like Ozone, Aerosol Index, Cloud Radiance Fraction Daily L3 Global 1.0x1.0 deg V003"]
   {:short-name "OMTO3d"
    :virtual-collections [{:entry-title "OMI/Aura TOMS-Like Column Amount Ozone and UV Aerosol Index Daily L3 Global 1.0x1.0 deg V003"
                           :short-name "OMTO3d_O3_AI"}]}
   ["GSFCS4PA" "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006"]
   {:short-name "AIRX3STD"
    :virtual-collections [{:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Water Vapor Mass Mixing Ratio V006"
                           :short-name "AIRX3STD_006_H2O_MMR_Surf"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Outgoing Longwave Radiation V006"
                           :short-name "AIRX3STD_006_OLR"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Surface Air Temperature V006"
                           :short-name "AIRX3STD_006_SurfAirTemp"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Surface Skin Temperature V006"
                           :short-name "AIRX3STD_006_SurfSkinTemp"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Total Carbon Monoxide V006"
                           :short-name "AIRX3STD_006_TotCO"}]}
   ["GSFCS4PA" "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006"]
   {:short-name "AIRX3STM"
    :virtual-collections [{:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Clear Sky Outgoing Longwave Flux V006"
                           :short-name "AIRX3STM_006_ClrOLR"}]}
   ["LPDAAC_ECS" "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"]
   {:short-name "AST_L1T"
    :virtual-collections [{:entry-title "ASTER Level 1 Full Resolution Browse Thermal Infrared V003"
                           :short-name "AST_FRBT"
                           :matcher (matches-on-psa "FullResolutionThermalBrowseAvailable" "YES")}
                          {:entry-title "ASTER Level 1 Full Resolution Browse Visible Near Infrared V003"
                           :short-name "AST_FRBV"
                           :matcher (matches-on-psa "FullResolutionVisibleBrowseAvailable" "YES")}]}})

(def virtual-product-to-source-config
  "A map derived from the map source-to-virtual-product-config. This map consists of keys which are
  a combination of provider id and entry title for each virtual product and values which are made up
  of short name, source entry title and source short name for each of the keys"
  (into {}
        (for [[[provider-id source-entry-title] vp-config] source-to-virtual-product-config
              :let [{:keys [short-name virtual-collections]} vp-config]
              virtual-collection virtual-collections]
          [[provider-id (:entry-title virtual-collection)]
           {:short-name (:short-name virtual-collection)
            :matcher (:matcher virtual-collection)
            :source-entry-title source-entry-title
            :source-short-name short-name}])))

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
    "OMUVBd.003:OMI-Aura_L3-OMUVBd_2005m0305_v003-2014m0117t163602.he5"]
   ["GSFCS4PA" "OMI/Aura TOMS-Like Ozone, Aerosol Index, Cloud Radiance Fraction Daily L3 Global 1.0x1.0 deg V003"]
   ["OMTO3d.003:OMI-Aura_L3-OMTO3d_2004m1001_v003-2012m0405t174138.he5"
    "OMTO3d.003:OMI-Aura_L3-OMTO3d_2004m1002_v003-2012m0405t174153.he5"
    "OMTO3d.003:OMI-Aura_L3-OMTO3d_2004m1003_v003-2012m0405t174154.he5"]
   ["GSFCS4PA" "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006"]
   ["AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
    "AIRX3STD.006:AIRS.2002.09.01.L3.RetStd001.v6.0.9.0.G13208004820.hdf"
    "AIRX3STD.006:AIRS.2002.09.02.L3.RetStd001.v6.0.9.0.G13208011303.hdf"]
   ["GSFCS4PA" "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006"]
   ["AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
    "AIRX3STM.006:AIRS.2002.10.01.L3.RetStd031.v6.0.9.0.G13211133235.hdf"
    "AIRX3STM.006:AIRS.2002.11.01.L3.RetStd030.v6.0.9.0.G13209234123.hdf"]
   ["LPDAAC_ECS" "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"]
   ["SC:AST_L1T.003:2148809731"
    "SC:AST_L1T.003:2148809742"
    "SC:AST_L1T.003:2148809779"]})

(defmulti generate-granule-ur
  "Generates a new granule ur for the virtual collection"
  (fn [provider-id source-short-name virtual-short-name granule-ur]
    [provider-id source-short-name]))

(defmethod generate-granule-ur :default
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

(defmethod compute-source-granule-ur :default
  [provider-id source-short-name virtual-short-name virtual-granule-ur]
  (str/replace-first virtual-granule-ur virtual-short-name source-short-name))

(def source-granule-ur-additional-attr-name
  "The name of the additional attribute used to store the granule-ur of the source granule"
  "source-granule-ur")

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
        (update-in [:product-specific-attributes]
                   conj
                   (umm-g/map->ProductSpecificAttributeRef
                     {:name source-granule-ur-additional-attr-name
                      :values [src-granule-ur]})))))

;; This is the main dispatching function used for updating virtual granules from the source
;; granules based on the source collection on which a virtual granule's collection is based. We
;; might want to move the functionality encapsulated within each of the dispatch functions to its
;; own file while leaving the default here. Or use a different way of updating the virtual
;; granule-umm like using templates.
(defmulti update-virtual-granule-umm
  "Dispatch function to update virtual granule umm based on source granule umm. All the non-core
  attributes of a virtual granule are inherited from source granule by default. This dispatch
  function is used for custom update of the virtual granule umm based on source granule umm."
  (fn [provider-id source-short-name virtual-short-name virtual-umm]
    [provider-id source-short-name]))

;; Default is to not do any update
(defmethod update-virtual-granule-umm :default
  [provider-id source-short-name source-umm virtual-umm]
  virtual-umm)

(defn- subset-opendap-resource-url
  "Update online-access-url of OMI/AURA virtual-collection to use an OpenDAP url. For example:
  http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc
  will be translated to
  http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat"
  [related-urls src-granule-ur opendap-subset]
  (seq (for [related-url related-urls
             ;; access urls shouldn't be present in the virtual granules
             :when (not= (:type related-url) "GET DATA")]
         (if (and (= (:type related-url) "OPENDAP DATA ACCESS")
                  (= (:mime-type related-url) "application/x-netcdf"))
           (assoc related-url
                  :url (str (:url related-url) "?" opendap-subset))
           related-url))))

(defn- remove-granule-size
  "Remove the size of the data granule if it is present"
  [virtual-umm]
  (if (get-in virtual-umm [:data-granule :size])
    (assoc-in virtual-umm [:data-granule :size] nil)
    virtual-umm))

(defn- update-related-urls
  "Generate the OpenDAP data access url for the virtual granule based on the OpenDAP link for the
  source dataset. Remove the size of the data from data granule as it is no longer valid since it
  represents the size of the original granule, not the subset."
  [provider-id source-short-name virtual-short-name virtual-umm opendap-subset]
  (let [source-granule-ur (compute-source-granule-ur
                            provider-id source-short-name virtual-short-name (:granule-ur virtual-umm))]
    (-> virtual-umm
        (update-in [:related-urls] subset-opendap-resource-url source-granule-ur opendap-subset)
        remove-granule-size)))

(defmethod update-virtual-granule-umm ["GSFCS4PA" "OMUVBd"]
  [provider-id source-short-name virtual-short-name virtual-umm]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat"))

(defmethod update-virtual-granule-umm ["GSFCS4PA" "OMTO3d"]
  [provider-id source-short-name virtual-short-name virtual-umm]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "ColumnAmountO3,UVAerosolIndex,lon,lat"))

(def airx3std-opendap-subsets
  "A map of short names of the virtual products based on AIRXSTD dataset to the string representing
  the corresponding OpenDAP url subset used in the generation of the online access urls for the
  virtual granule metadata being created"
  {"AIRX3STD_006_H2O_MMR_Surf" "H2O_MMR_A,H2OPrsLvls_A,Latitude,Longitude"
   "AIRX3STD_006_OLR" "OLR_A,OLR_D,Latitude,Longitude"
   "AIRX3STD_006_SurfAirTemp" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude"
   "AIRX3STD_006_SurfSkinTemp" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude"
   "AIRX3STD_006_TotCO" "TotCO_A,TotCO_D,Latitude,Longitude"})

(defmethod update-virtual-granule-umm ["GSFCS4PA" "AIRX3STD"]
  [provider-id source-short-name virtual-short-name virtual-umm]
  (let [virtual-entry-title (get-in virtual-umm [:collection-ref :entry-title])]
    (update-related-urls provider-id source-short-name virtual-short-name virtual-umm (get airx3std-opendap-subsets virtual-short-name))))

(defmethod update-virtual-granule-umm ["GSFCS4PA" "AIRX3STM"]
  [provider-id source-short-name virtual-short-name virtual-umm]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "ClrOLR_A,ClrOLR_D,Latitude,Longitude"))

(defn- update-ast-l1t-related-urls
  "Filter online access urls corresponding to the virtual collection from the source related urls"
  [virtual-umm virtual-short-name]
  (let [online-access-urls (filter #(= (:type %) "GET DATA") (:related-urls virtual-umm))
        frb-url-matches (fn [related-url suffix fmt]
                          (let [url (:url related-url)]
                            (or (.endsWith url suffix) (.contains url (format "FORMAT=%s" fmt)))))]
      (assoc virtual-umm :related-urls
             (cond
               (and (= "AST_FRBT" virtual-short-name)
                    ((matches-on-psa "FullResolutionThermalBrowseAvailable" "YES") virtual-umm))
               (seq (filter #(frb-url-matches % "_T.tif" "TIR") online-access-urls))

               (and (= "AST_FRBV" virtual-short-name)
                    ((matches-on-psa "FullResolutionVisibleBrowseAvailable" "YES") virtual-umm))
               (seq (filter #(frb-url-matches % "_V.tif" "VNIR") online-access-urls))))))


(defmethod update-virtual-granule-umm ["LPDAAC_ECS" "AST_L1T"]
  [provider-id source-short-name virtual-short-name virtual-umm]
  (-> virtual-umm
      (update-ast-l1t-related-urls virtual-short-name)
      (update-in [:product-specific-attributes]
                 (fn [psas] (remove #(#{"identifier_product_doi"
                                        "identifier_product_doi_authority"
                                        "FullResolutionVisibleBrowseAvailable"
                                        "FullResolutionThermalBrowseAvailable"}
                                        (:name %)) psas)))))


(defn generate-virtual-granule-umm
  "Generate the virtual granule umm based on source granule umm"
  [provider-id source-short-name source-umm virtual-coll]
  (let [virtual-granule-ur (generate-granule-ur
                             provider-id
                             source-short-name
                             (:short-name virtual-coll)
                             (:granule-ur source-umm))
        virtual-umm (update-core-fields source-umm virtual-granule-ur virtual-coll)
        virtual-short-name (:short-name virtual-coll)]
    (update-virtual-granule-umm provider-id source-short-name virtual-short-name virtual-umm)))
