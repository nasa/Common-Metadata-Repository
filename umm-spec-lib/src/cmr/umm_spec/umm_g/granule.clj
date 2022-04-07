(ns cmr.umm-spec.umm-g.granule
  "Contains functions for parsing UMM-G JSON into umm-lib granule model
  and generating UMM-G JSON from umm-lib granule model."
  (:require
   [cmr.umm-spec.umm-g.additional-attribute :as aa]
   [cmr.umm-spec.umm-g.data-granule :as data-granule]
   [cmr.umm-spec.umm-g.measured-parameters :as measured-parameters]
   [cmr.umm-spec.umm-g.orbit-calculated-spatial-domain :as ocsd]
   [cmr.umm-spec.umm-g.platform :as platform]
   [cmr.umm-spec.umm-g.projects :as projects]
   [cmr.umm-spec.umm-g.related-url :as related-url]
   [cmr.umm-spec.umm-g.spatial :as spatial]
   [cmr.umm-spec.umm-g.tiling-system :as tiling-system]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(def ^:private umm-g-metadata-specification
  "Defines the current UMM-G MetadataSpecification"
  {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.4"
   :Name "UMM-G"
   :Version "1.6.4"})

(defn- get-date-by-type
  "Returns the date of the given type from the given provider dates"
  [provider-dates date-type]
  (some #(when (= date-type (:Type %)) (:Date %)) provider-dates))

(defn- umm-g->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [umm-g]
  (let [provider-dates (:ProviderDates umm-g)
        ;; umm-lib Granule model does not have create-time, so ignore it for now.
        ; create-time (get-date-by-type provider-dates "Create")
        insert-time (get-date-by-type provider-dates "Insert")
        update-time (get-date-by-type provider-dates "Update")
        delete-time (get-date-by-type provider-dates "Delete")]
    (g/map->DataProviderTimestamps
     {;;:create-time create-time
      :insert-time insert-time
      :update-time update-time
      :delete-time delete-time})))

(defn- umm-g->CollectionRef
  "Returns a UMM ref element from a parsed UMM-G JSON"
  [umm-g-json]
  (let [collection-ref (:CollectionReference umm-g-json)]
    (g/map->CollectionRef {:entry-title (:EntryTitle collection-ref)
                           :short-name (:ShortName collection-ref)
                           :version-id (:Version collection-ref)})))

(defn umm-g->Temporal
  "Returns a UMM Temporal from a parsed UMM-G JSON"
  [umm-g-json]
  (when-let [temporal (:TemporalExtent umm-g-json)]
    (let [range-date-time (when-let [range-date-time (:RangeDateTime temporal)]
                            (umm-c/map->RangeDateTime
                             {:beginning-date-time (:BeginningDateTime range-date-time)
                              :ending-date-time (:EndingDateTime range-date-time)}))
          single-date-time (:SingleDateTime temporal)]
      (g/map->GranuleTemporal
       {:range-date-time range-date-time
        :single-date-time single-date-time}))))

(defn umm-g->PGEVersionClass
  "Returns a UMM PGEVersionClass from a parsed UMM-G JSON's PGEVersionClass"
  [pge-version-class]
  (when-let [{:keys [PGEName PGEVersion]} pge-version-class]
    (g/map->PGEVersionClass {:pge-name PGEName
                             :pge-version PGEVersion})))

(defn PGEVersionClass->umm-g-pge-version-class
  [pge-version-class]
  (when-let [{:keys [pge-name pge-version]} pge-version-class]
    {:PGEName pge-name
     :PGEVersion pge-version}))

(defn umm-g->Granule
  "Returns a UMM Granule from a parsed UMM-G JSON"
  [umm-g-json]
  (let [coll-ref (umm-g->CollectionRef umm-g-json)]
    (g/map->UmmGranule
     {:granule-ur (:GranuleUR umm-g-json)
      :data-provider-timestamps (umm-g->DataProviderTimestamps umm-g-json)
      :collection-ref coll-ref
      :data-granule (data-granule/umm-g-data-granule->DataGranule (:DataGranule umm-g-json))
      :pge-version-class (umm-g->PGEVersionClass (:PGEVersionClass umm-g-json))
      :temporal (umm-g->Temporal umm-g-json)
      :orbit-calculated-spatial-domains (ocsd/umm-g-orbit-calculated-spatial-domains->OrbitCalculatedSpatialDomains
                                         (:OrbitCalculatedSpatialDomains umm-g-json))
      :platform-refs (platform/umm-g-platforms->PlatformRefs (:Platforms umm-g-json))
      :project-refs (projects/umm-g-projects->ProjectRefs (:Projects umm-g-json))
      :access-value (get-in umm-g-json [:AccessConstraints :Value])
      :cloud-cover (:CloudCover umm-g-json)
      :two-d-coordinate-system (tiling-system/umm-g-tiling-identification-system->TwoDCoordinateSystem
                                 (:TilingIdentificationSystem umm-g-json))
      :spatial-coverage (spatial/umm-g-spatial-extent->SpatialCoverage umm-g-json)
      :related-urls (related-url/umm-g-related-urls->RelatedURLs (:RelatedUrls umm-g-json))
      :measured-parameters (measured-parameters/umm-g-measured-parameters->MeasuredParameters
                            (:MeasuredParameters umm-g-json))
      :product-specific-attributes (aa/umm-g-additional-attributes->ProductSpecificAttributeRefs
                                     (:AdditionalAttributes umm-g-json))})))

(defn Granule->umm-g
  "Returns UMM-G JSON from a umm-lib Granule"
  [granule]
  (let [{:keys [granule-ur data-granule pge-version-class access-value temporal orbit-calculated-spatial-domains
                platform-refs project-refs cloud-cover related-urls product-specific-attributes
                spatial-coverage orbit two-d-coordinate-system measured-parameters
                collection-ref data-provider-timestamps]} granule
        {:keys [entry-title short-name version-id entry-id]} collection-ref
        {:keys [insert-time update-time delete-time]} data-provider-timestamps
        insert-time (when insert-time
                      {:Date (str insert-time)
                       :Type "Insert"})
        update-time (when update-time
                      {:Date (str update-time)
                       :Type "Update"})
        delete-time (when delete-time
                      {:Date (str delete-time)
                       :Type "Delete"})]
    {:GranuleUR granule-ur
     :ProviderDates (vec (remove nil? [insert-time update-time delete-time]))
     :CollectionReference (if (some? entry-title)
                            {:EntryTitle entry-title}
                            {:ShortName short-name
                             :Version version-id})
     :TemporalExtent (if-let [single-date-time (:single-date-time temporal)]
                       {:SingleDateTime (str single-date-time)}
                       (when-let [range-date-time (:range-date-time temporal)]
                         {:RangeDateTime
                          {:BeginningDateTime (str (:beginning-date-time range-date-time))
                           :EndingDateTime (when-let [ending-date-time (:ending-date-time range-date-time)]
                                             (str ending-date-time))}}))
     :SpatialExtent (spatial/SpatialCoverage->umm-g-spatial-extent spatial-coverage)
     :OrbitCalculatedSpatialDomains (ocsd/OrbitCalculatedSpatialDomains->umm-g-orbit-calculated-spatial-domains
                                     orbit-calculated-spatial-domains)
     :Platforms (platform/PlatformRefs->umm-g-platforms platform-refs)
     :CloudCover cloud-cover
     :AccessConstraints (when access-value {:Value access-value})
     :Projects (projects/ProjectRefs->umm-g-projects project-refs)
     :DataGranule (data-granule/DataGranule->umm-g-data-granule data-granule)
     :PGEVersionClass (PGEVersionClass->umm-g-pge-version-class pge-version-class)
     :TilingIdentificationSystem (tiling-system/TwoDCoordinateSystem->umm-g-tiling-identification-system
                                   two-d-coordinate-system)
     :AdditionalAttributes (aa/ProductSpecificAttributeRefs->umm-g-additional-attributes
                             product-specific-attributes)
     :MeasuredParameters (measured-parameters/MeasuredParameters->umm-g-measured-parameters
                          measured-parameters)
     :RelatedUrls (related-url/RelatedURLs->umm-g-related-urls related-urls)
     :MetadataSpecification umm-g-metadata-specification}))
