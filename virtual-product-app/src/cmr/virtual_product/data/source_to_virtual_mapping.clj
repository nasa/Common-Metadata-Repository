(ns cmr.virtual-product.data.source-to-virtual-mapping
  "Defines source to vritual granule mapping rules."
  (:require
   [clojure.string :as str]
   [cmr.common.mime-types :as mt]
   [cmr.umm.related-url-helper :as ruh]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.virtual-product.config :as vp-config]
   [cmr.virtual-product.data.ast-l1a :as l1a])
  (:import
   (java.util.regex Pattern)))

(def source-granule-ur-additional-attr-name
  "The name of the additional attribute used to store the granule-ur of the source granule"
  "source_granule_ur")

(defn provider-alias->provider-id
  "Get the provider-id for the given provider alias. If the alias is provider-id itself, returns
  provider-id, otherwise returns the matching provider-id or nil if no such alias is defined."
  [alias]
  (let [provider-aliases (vp-config/virtual-product-provider-aliases)]
    ;; Check if alias is one of the provider ids defined in provider aliases configuration
    (if (contains? provider-aliases alias)
      alias
      (some (fn [[provider-id aliases]]
              (when (contains? aliases alias) provider-id)) provider-aliases))))

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

(defn- sanitize-version-id
  "When version-id is nil, return nil
   When version-id is a number, return string version with preceeding zeros
   When version-id is a string, parse into number, then return as string with preceeding zeros.
   When version-id is a string without a number, return 001. (This should never be the case)"
  [version-id]
  (when-not (empty? version-id)
    (if (number? version-id)
      (format "%03d" version-id)
      (as-> (re-find #"\d+" version-id) version-id
            (or version-id "1")
            (Integer/parseInt version-id)
            (format "%03d" version-id)))))

(def day-granule? (matches-value [:data-granule :day-night] "DAY"))
(def tir-mode? (matches-on-psa "TIR_ObservationMode" "ON"))
(def swir-mode? (matches-on-psa "SWIR_ObservationMode" "ON"))
(def vnir1-mode? (matches-on-psa "VNIR1_ObservationMode" "ON"))
(def vnir2-mode? (matches-on-psa "VNIR2_ObservationMode" "ON"))

(def source-to-virtual-product-mapping
  "A map of source collection provider id and entry titles to virtual product configs"
  {["LPDAAC_ECS" "ASTER L1A Reconstructed Unprocessed Instrument Data V003"]
   {:short-name "AST_L1A"
    :virtual-collections [
                          {:entry-title "ASTER L2 Surface Emissivity V003"
                           :short-name "AST_05"
                           :version-id "003"
                           :matcher tir-mode?}
                          {:entry-title "ASTER L2 Surface Reflectance SWIR and ASTER L2 Surface Reflectance VNIR V003"
                           :short-name "AST_07"
                           :version-id "003"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER L2 Surface Reflectance VNIR and Crosstalk Corrected SWIR V003"
                           :short-name "AST_07XT"
                           :version-id "003"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER L2 Surface Temperature V003"
                           :short-name "AST_08"
                           :version-id "003"
                           :matcher tir-mode?}
                          {:entry-title "ASTER L2 Surface Radiance VNIR and SWIR V003"
                           :short-name "AST_09"
                           :version-id "003"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER L2 Surface Radiance - VNIR and Crosstalk Corrected SWIR V003"
                           :short-name "AST_09XT"
                           :version-id "003"
                           :matcher (match-all swir-mode? vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER L2 Surface Radiance TIR V003"
                           :short-name "AST_09T"
                           :version-id "003"
                           :matcher tir-mode?}
                          {:entry-title "ASTER Digital Elevation Model V003"
                           :short-name "AST14DEM"
                           :version-id "003"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER Registered Radiance at the Sensor - Orthorectified V003"
                           :short-name "AST14OTH"
                           :version-id "003"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER Orthorectified Digital Elevation Model (DEM) V003"
                           :short-name "AST14DMO"
                           :version-id "003"
                           :matcher (match-all vnir1-mode? vnir2-mode? day-granule?)}
                          {:entry-title "ASTER L1B Registered Radiance at the Sensor V003"
                           :short-name "AST_L1B"
                           :version-id "003"}
                          {:entry-title "ASTER Level 1 Precision Terrain Corrected Registered At-Sensor Radiance V031"
                           :short-name "AST_L1T"
                           :version-id "031"}]}
   ["GES_DISC" "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd) at GES DISC"]
   {:short-name "OMUVBd"
    :virtual-collections [
                          {:entry-title "OMI/Aura Surface UVB UV Index, Erythemal Dose, and Erythemal Dose Rate Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd_ErythemalUV) at GES DISC"
                           :short-name "OMUVBd_ErythemalUV"
                           :version-id "003"}]}
   ["GES_DISC" "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"]
   {:short-name "AIRX3STD"
    :virtual-collections [
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Water Vapor Mass Mixing Ratio V006 (AIRX3STD_H2O_MMR_Surf) at GES DISC"
                           :short-name "AIRX3STD_H2O_MMR_Surf"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Outgoing Longwave Radiation V006 (AIRX3STD_OLR) at GES DISC"
                           :short-name "AIRX3STD_OLR"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Surface Air Temperature V006 (AIRX3STD_SurfAirTemp) at GES DISC"
                           :short-name "AIRX3STD_SurfAirTemp"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Surface Skin Temperature V006 (AIRX3STD_SurfSkinTemp) at GES DISC"
                           :short-name "AIRX3STD_SurfSkinTemp"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Total Carbon Monoxide V006 (AIRX3STD_TotCO) at GES DISC"
                           :short-name "AIRX3STD_TotCO"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Outgoing Longwave Radiation Clear Sky V006 (AIRX3STD_ClrOLR) at GES DISC"
                           :short-name "AIRX3STD_ClrOLR"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) Methane Total Column V006 (AIRX3STD_TotCH4) at GES DISC"
                           :short-name "AIRX3STD_TotCH4"
                           :version-id "006"}]}
   ["GES_DISC" "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STM) at GES DISC"]
   {:short-name "AIRX3STM"
    :virtual-collections [
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Clear Sky Outgoing Longwave Flux V006 (AIRX3STM_ClrOLR) at GES DISC"
                           :short-name "AIRX3STM_ClrOLR"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Water Vapor Mass Mixing Ratio at Surface V006 (AIRX3STM_H2O_MMR_Surf) at GES DISC"
                           :short-name "AIRX3STM_H2O_MMR_Surf"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Outgoing Longwave Radiation V006 (AIRX3STM_OLR) at GES DISC"
                           :short-name "AIRX3STM_OLR"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Surface Air Temperature V006 (AIRX3STM_SurfAirTemp) at GES DISC"
                           :short-name "AIRX3STM_SurfAirTemp"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Surface Skin Temperature V006 (AIRX3STM_SurfSkinTemp) at GES DISC"
                           :short-name "AIRX3STM_SurfSkinTemp"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Total Carbon Monoxide V006 (AIRX3STM_TotCO) at GES DISC"
                           :short-name "AIRX3STM_TotCO"
                           :version-id "006"}
                          {:entry-title "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) Methane Total Column V006 (AIRX3STM_TotCH4) at GES DISC"
                           :short-name "AIRX3STM_TotCH4"
                           :version-id "006"}]}
   ["LPDAAC_ECS" "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"]
   {:short-name "AST_L1T"
    :virtual-collections [
                          {:entry-title "ASTER Level 1 Full Resolution Browse Thermal Infrared V003"
                           :short-name "AST_FRBT"
                           :version-id "003"
                           :matcher (matches-on-psa "FullResolutionThermalBrowseAvailable" "YES")}
                          {:entry-title "ASTER Level 1 Full Resolution Browse Visible Near Infrared V003"
                           :short-name "AST_FRBV"
                           :version-id "003"
                           :matcher (matches-on-psa "FullResolutionVisibleBrowseAvailable" "YES")}]}
   ["GES_DISC" "GLDAS Noah Land Surface Model L4 3 hourly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_3H) at GES DISC"]
   {:short-name "GLDAS_NOAH10_3H"
    :virtual-collections [
                          {:entry-title "GLDAS Noah Land Surface Model L4 3 hourly 1.0 x 1.0 degree Rain Rate, Avg. Surface Skin Temp., Soil Moisture V2.0 (GLDAS_NOAH10_3Hourly) at GES DISC"
                           :short-name "GLDAS_NOAH10_3Hourly"
                           :version-id "2.0"}]}
   ["GES_DISC" "GLDAS Noah Land Surface Model L4 Monthly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_M) at GES DISC"]
   {:short-name "GLDAS_NOAH10_M"
    :virtual-collections [
                          {:entry-title "GLDAS Noah Land Surface Model L4 Monthly 1.0 x 1.0 degree Rain Rate, Avg. Surface Skin Temp., Soil Moisture V2.0 (GLDAS_NOAH10_Monthly) at GES DISC"
                           :short-name "GLDAS_NOAH10_Monthly"
                           :version-id "2.0"}]}
   ["DEMO_PROV" "DEMO_PROV VP Test"]
   {:short-name "VP_TEST"
    :virtual-collections [
                          {:entry-title "A Virtual Product test collection."
                           :short-name "VP_TEST_DEST"
                           :version-id "001"}]}
   ["EEDTEST" "EEDTEST VP Test"]
   {:short-name "VP_TEST"
    :virtual-collections [
                          {:entry-title "A Virtual Product test collection."
                           :short-name "VP_TEST_DEST"
                           :version-id "001"}]}})

(def virtual-product-to-source-mapping
  "A map derived from the map source-to-virtual-product-mapping. This map consists of keys which are
  a combination of provider id and entry title for each virtual product and values which are made up
  of short name, source entry title and source short name for each of the keys"
  (into {}
        (for [[[provider-id source-entry-title] vp-config] source-to-virtual-product-mapping
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
    "SC:AST_L1A.003:2006227722"]
   ["GES_DISC" "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd) at GES DISC"]
   ["OMUVBd.003:OMI-Aura_L3-OMUVBd_2004m1001_v003-2013m0314t081851.he5"
    "OMUVBd.003:OMI-Aura_L3-OMUVBd_2004m1012_v003-2014m0117t110510.he5"]
   ["GES_DISC" "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"]
   ["AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
    "AIRX3STD.006:AIRS.2002.09.01.L3.RetStd001.v6.0.9.0.G13208004820.hdf"]
   ["GES_DISC" "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STM) at GES DISC"]
   ["AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
    "AIRX3STM.006:AIRS.2002.10.01.L3.RetStd031.v6.0.9.0.G13211133235.hdf"]
   ["LPDAAC_ECS" "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"]
   ["SC:AST_L1T.003:2148809731"
    "SC:AST_L1T.003:2148809742"]
   ["GES_DISC" "GLDAS Noah Land Surface Model L4 3 hourly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_3H) at GES DISC"]
   ["GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4"
    "GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0600.020.nc4"]
   ["GES_DISC" "GLDAS Noah Land Surface Model L4 Monthly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_M) at GES DISC"]
   ["GLDAS_NOAH10_M.2.0:GLDAS_NOAH10_M.A194801.020.nc4"
    "GLDAS_NOAH10_M.2.0:GLDAS_NOAH10_M.A194802.020.nc4"]})

(defmulti generate-granule-ur
  "Generates a new granule ur for the virtual collection"
  (fn [provider-id source-short-name virtual-granule granule-ur]
    source-short-name))

(defmethod generate-granule-ur :default
  [provider-id source-short-name virtual-granule granule-ur]
  (str/replace-first granule-ur source-short-name (:short-name virtual-granule)))

(defmethod generate-granule-ur "AST_L1A"
  [provider-id source-short-name virtual-granule granule-ur]
  (let [virtual-version-id (sanitize-version-id (:version-id virtual-granule))]
    (as-> (str/replace-first granule-ur source-short-name (:short-name virtual-granule)) granule-ur
          (if virtual-version-id
            (str/replace-first granule-ur #"\.\d\d\d" (str "." virtual-version-id))
            granule-ur))))

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
    [(provider-alias->provider-id provider-id) source-short-name]))

(defmethod compute-source-granule-ur :default
  [provider-id source-short-name virtual-short-name virtual-granule-ur]
  (str/replace-first virtual-granule-ur virtual-short-name source-short-name))

(defmethod compute-source-granule-ur ["LPDAAC_ECS" "AST_L1A"]
  [provider-id source-short-name virtual-short-name virtual-granule-ur]
  (as-> (str/replace-first virtual-granule-ur virtual-short-name source-short-name) virtual-granule-ur
        (str/replace-first virtual-granule-ur #"\.\d\d\d:" ".003:")))

(defn- update-core-fields
  "Update the core set of fields in the source granule umm to create the virtual granule umm. These
  updates are common across all the granules in all the virtual collections. The remaining fields
  are inherited by the virtual granule automatically from the source granule unless overridden by
  the dispatch function update-virtual-granule-umm"
  [src-granule-umm virt-granule-ur virtual-coll]
  (let [src-granule-ur (:granule-ur src-granule-umm)]
    (-> src-granule-umm
        (assoc :granule-ur virt-granule-ur
               :collection-ref (umm-g/map->CollectionRef
                                 (select-keys virtual-coll [:short-name :version-id])))
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
  (fn [virtual-umm provider-id source-short-name virtual-short-name]
    [(provider-alias->provider-id provider-id) source-short-name]))

;; Default is to not do any update
(defmethod update-virtual-granule-umm :default
  [virtual-umm provider-id source-short-name source-umm]
  virtual-umm)

(defn- subset-opendap-resource-url
  "Update online-resource-url of OMI/AURA source granule to use an OpenDAP url as an online-access-url.
  For example:
  http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5
  of OnlineResourceURL that has type of 'USE SERVICE API' will be converted into
  http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat
  as an OnlineResourceURL in the virtual granule and the other OnlineResourceURLs or OnlineAccessURLs
  in the source granule will be dropped."
  [related-urls src-granule-ur opendap-subset]
  (seq (for [related-url related-urls
             ;; only opendap OnlineResourceUrls in source granule should be present in the virtual granules
             :when (= (:type related-url) "USE SERVICE API")]
         ;; only URL is kept in virtual granule OnlineResourceURL
         (umm-c/map->RelatedURL
           {:type "USE SERVICE API"
            :sub-type "OPENDAP DATA"
            :url (str (:url related-url) ".nc?" opendap-subset)}))))

(defn- remove-granule-size
  "Remove the size of the data granule if it is present"
  [virtual-umm]
  (if (get-in virtual-umm [:data-granule :size])
    (assoc-in virtual-umm [:data-granule :size] nil)
    virtual-umm))

(defn- update-related-urls
  "Generate the OpenDAP online resource url for the virtual granule based on the OpenDAP link for the
  source dataset. Remove the size of the data from data granule as it is no longer valid since it
  represents the size of the original granule, not the subset."
  [provider-id source-short-name virtual-short-name virtual-umm opendap-subset]
  (let [source-granule-ur (compute-source-granule-ur
                            provider-id source-short-name virtual-short-name (:granule-ur virtual-umm))]
    (-> virtual-umm
        (update-in [:related-urls] subset-opendap-resource-url source-granule-ur opendap-subset)
        remove-granule-size)))

(defmethod update-virtual-granule-umm ["GES_DISC" "OMUVBd"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat"))

(def airx3std-opendap-subsets
  "A map of short names of the virtual products based on AIRXSTD dataset to the string representing
  the corresponding OpenDAP url subset used in the generation of the online access urls for the
  virtual granule metadata being created"
  {"AIRX3STD_H2O_MMR_Surf" "H2O_MMR_A,H2O_MMR_D,Latitude,Longitude"
   "AIRX3STD_OLR" "OLR_A,OLR_D,Latitude,Longitude"
   "AIRX3STD_SurfAirTemp" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude"
   "AIRX3STD_SurfSkinTemp" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude"
   "AIRX3STD_TotCO" "TotCO_A,TotCO_D,Latitude,Longitude"
   "AIRX3STD_ClrOLR" "ClrOLR_A,ClrOLR_D,Latitude,Longitude"
   "AIRX3STD_TotCH4" "TotCH4_A,TotCH4_D,Latitude,Longitude"})

(defmethod update-virtual-granule-umm ["GES_DISC" "AIRX3STD"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (let [virtual-entry-title (get-in virtual-umm [:collection-ref :entry-title])]
    (update-related-urls provider-id source-short-name virtual-short-name virtual-umm (get airx3std-opendap-subsets virtual-short-name))))

(def airx3stm-opendap-subsets
  "A map of short names of the virtual products based on AIRXSTM dataset to the string representing
  the corresponding OpenDAP url subset used in the generation of the online access urls for the
  virtual granule metadata being created"
  {"AIRX3STM_ClrOLR" "ClrOLR_A,ClrOLR_D,Latitude,Longitude"
   "AIRX3STM_H2O_MMR_Surf" "H2O_MMR_A,H2O_MMR_D,Latitude,Longitude"
   "AIRX3STM_OLR" "OLR_A,OLR_D,Latitude,Longitude"
   "AIRX3STM_SurfAirTemp" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude"
   "AIRX3STM_SurfSkinTemp" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude"
   "AIRX3STM_TotCO" "TotCO_A,TotCO_D,Latitude,Longitude"
   "AIRX3STM_TotCH4" "TotCH4_A,TotCH4_D,Latitude,Longitude"})

(defmethod update-virtual-granule-umm ["GES_DISC" "AIRX3STM"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm (get airx3stm-opendap-subsets virtual-short-name)))

(defmethod update-virtual-granule-umm ["GES_DISC" "GLDAS_NOAH10_3H"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "Rainf_tavg,AvgSurfT_inst,SoilMoi0_10cm_inst,time,lat,lon"))

(defmethod update-virtual-granule-umm ["GES_DISC" "GLDAS_NOAH10_M"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (update-related-urls provider-id source-short-name virtual-short-name virtual-umm "Rainf_tavg,AvgSurfT_inst,SoilMoi0_10cm_inst,time,lat,lon"))

(defn- ast-l1t-virtual-online-access-urls
  "Returns the online access urls for virtual granule of the given AST_L1T granule"
  [virtual-umm virtual-short-name]
  (let [online-access-urls (filter ruh/downloadable-url? (:related-urls virtual-umm))
        frb-url-matches (fn [related-url suffix fmt]
                          (let [url (:url related-url)]
                            (or (.endsWith ^String url suffix)
                                (.contains ^String url (format "FORMAT=%s" fmt)))))]
    (cond
      (and (= "AST_FRBT" virtual-short-name)
           ((matches-on-psa "FullResolutionThermalBrowseAvailable" "YES") virtual-umm))
      (seq (filter #(frb-url-matches % "_T.tif" "TIR") online-access-urls))

      (and (= "AST_FRBV" virtual-short-name)
           ((matches-on-psa "FullResolutionVisibleBrowseAvailable" "YES") virtual-umm))
      (seq (filter #(frb-url-matches % "_V.tif" "VNIR") online-access-urls)))))

(defn- ast-l1t-virtual-browse-qa-urls
  "Returns the online resource urls for virtual granule of the given AST_L1T granule"
  [virtual-umm virtual-short-name]
  (let [browse-qa-urls (remove ruh/downloadable-url? (:related-urls virtual-umm))
        [browse-psa browse-suffix] (if (= "AST_FRBT" virtual-short-name)
                                     ["FullResolutionThermalBrowseAvailable" ".TIR.jpg"]
                                     ["FullResolutionVisibleBrowseAvailable" ".VNIR.jpg"])
        psa-match? ((matches-on-psa browse-psa "YES") virtual-umm)
        frb-url-matches (fn [related-url]
                          (or (and (.endsWith ^String (:url related-url) browse-suffix) psa-match?)
                              (.endsWith ^String (:url related-url) ".QA.jpg")
                              (.endsWith ^String (:url related-url) "_QA.txt")))]
    (seq (filter frb-url-matches browse-qa-urls))))

(defn- update-ast-l1t-related-urls
  "Filter online access urls corresponding to the virtual collection from the source related urls"
  [virtual-umm virtual-short-name]
  (assoc virtual-umm :related-urls
         (concat (ast-l1t-virtual-online-access-urls virtual-umm virtual-short-name)
                 (ast-l1t-virtual-browse-qa-urls virtual-umm virtual-short-name))))


(defmethod update-virtual-granule-umm ["LPDAAC_ECS" "AST_L1T"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (-> virtual-umm
      (update-ast-l1t-related-urls virtual-short-name)
      (update-in [:product-specific-attributes]
                 (fn [psas] (remove #(#{"identifier_product_doi"
                                        "identifier_product_doi_authority"
                                        "FullResolutionVisibleBrowseAvailable"
                                        "FullResolutionThermalBrowseAvailable"}
                                       (:name %)) psas)))))

(defmethod update-virtual-granule-umm ["LPDAAC_ECS" "AST_L1A"]
  [virtual-umm provider-id source-short-name virtual-short-name]
  (update-in
    virtual-umm
    [:product-specific-attributes]
    (fn [psas]
      (filter #((l1a/short-name->additional-attributes virtual-short-name)
                (:name %)) psas))))

(defn generate-virtual-granule-umm
  "Generate the virtual granule umm based on source granule umm"
  [provider-id source-short-name source-umm virtual-coll]
  (let [virtual-short-name (:short-name virtual-coll)
        virtual-granule-ur (generate-granule-ur
                             provider-id
                             source-short-name
                             virtual-coll
                             (:granule-ur source-umm))]
    (-> source-umm
        ;; Remove measured parameters from virtual granules
        (assoc :measured-parameters nil)
        (update-core-fields virtual-granule-ur virtual-coll)
        (update-virtual-granule-umm provider-id source-short-name virtual-short-name))))
