(ns cmr.umm-spec.umm-g.granule
  "Contains functions for parsing UMM-G JSON into umm-lib granule model
  and generating UMM-G JSON from umm-lib granule model."
  (:require
   [cheshire.core :as json]
   [cmr.umm.umm-granule :as g]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.common.util :as util])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- get-date-by-type
  "Returns the date of the given type from the given provider dates"
  [provider-dates date-type]
  (some #(when (= date-type (:Type %)) (:Date %)) provider-dates))

(defn- umm-g->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [umm-g]
  (let [provider-dates (:ProviderDates umm-g)
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

(defn umm-g->Granule
  "Returns a UMM Granule from a parsed UMM-G JSON"
  [umm-g-json]
  (let [coll-ref (umm-g->CollectionRef umm-g-json)]
    (g/map->UmmGranule {:granule-ur (:GranuleUR umm-g-json)
                        :data-provider-timestamps (umm-g->DataProviderTimestamps umm-g-json)
                        :collection-ref coll-ref
                        ; :data-granule (xml-elem->DataGranule umm-g-json)
                        ; :access-value (cx/double-at-path umm-g-json [:RestrictionFlag])
                        ; :temporal (gt/xml-elem->Temporal umm-g-json)
                        ; :orbit-calculated-spatial-domains (ocsd/xml-elem->orbit-calculated-spatial-domains umm-g-json)
                        ; :platform-refs (p-ref/xml-elem->PlatformRefs umm-g-json)
                        ; :project-refs (xml-elem->project-refs umm-g-json)
                        ; :cloud-cover (cx/double-at-path umm-g-json [:CloudCover])
                        ; :two-d-coordinate-system (two-d/xml-elem->TwoDCoordinateSystem umm-g-json)
                        ; :related-urls (ru/xml-elem->related-urls umm-g-json)
                        ; :spatial-coverage (xml-elem->SpatialCoverage umm-g-json)
                        ; :measured-parameters (mp/xml-elem->MeasuredParameters umm-g-json)
                        ; :product-specific-attributes (psa/xml-elem->ProductSpecificAttributeRefs umm-g-json)
                        })))

(defn Granule->umm-g
  "Returns UMM-G JSON from a umm-lib Granule"
  [granule]
  (let [{:keys [granule-ur data-granule access-value temporal orbit-calculated-spatial-domains
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
     }
    ))
