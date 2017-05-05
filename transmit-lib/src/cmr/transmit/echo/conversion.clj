(ns cmr.transmit.echo.conversion
  "Contains functions for converting between CMR style and ECHO style objects"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.util :as util]
            [camel-snake-kebab.core :as csk]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [schema.core :as s]))

(defn echo-sid->cmr-sid
  "Converts an ECHO sid into a cmr style sid.

  input: {:sid {:user_authorization_type_sid {:user_authorization_type \"GUEST\"}}}
  output: :guest

  input: {:sid {:user_authorization_type_sid {:user_authorization_type \"REGISTERED\"}}}
  output: :registered

  input {:sid {:group_sid {:group_guid \"3730376E-4DCF-53EE-90ED-FE945351A64F\"}}}}
  output \"3730376E-4DCF-53EE-90ED-FE945351A64F\""
  [sid]
  (let [sid (util/map-keys->kebab-case (:sid sid))]
    (or (get-in sid [:group-sid :group-guid])
        (-> sid
            :user-authorization-type-sid
            :user-authorization-type
            str/lower-case
            keyword))))

(defn cmr-sid->echo-sid
  "Converts a cmr style sid to an ECHO sid"
  [sid]
  (if (keyword? sid)
    {:sid {:user_authorization_type_sid
           {:user_authorization_type (-> sid name str/upper-case)}}}
    {:sid {:group_sid {:group_guid sid}}}))


(defn- echo-ace->cmr-ace
  "Cleans up the access control entry of an ACL."
  [ace]
  (let [{:keys [permissions]} ace
        permissions (mapv (comp keyword str/lower-case) permissions)
        cmr-sid (echo-sid->cmr-sid ace)]
    (if (keyword? cmr-sid)
      {:permissions permissions
       :user-type cmr-sid}
      {:permissions permissions
       :group-guid cmr-sid})))

(defn cmr-ace->echo-ace
  [ace]
  (let [{:keys [permissions group-guid user-type]} ace]
    (merge {:permissions (mapv (comp str/upper-case name) permissions)}
           (cmr-sid->echo-sid (or group-guid user-type)))))


(def ^:private echo-temporal-formatter
  "A clj-time formatter that can parse the times returned by ECHO in ACL temporal filters."
  (f/formatter "EEE MMM dd HH:mm:ss Z yyyy"))

(defn- parse-echo-temporal-date
  "Parses an ECHO temporal date which is of the format Tue Sep 01 12:22:41 -0400 2015. It may contain
  UTC for the timezone as well."
  [s]
  (f/parse echo-temporal-formatter (str/replace s "UTC" "+0000")))

(defn- generate-echo-temporal-date
  "Generates an ECHO temporal date from a clj-time date."
  [dt]
  (f/unparse echo-temporal-formatter dt))

(defn- echo-temporal->cmr-temporal
  [rt]
  (-> rt
      (update-in [:mask] csk/->kebab-case-keyword)
      (update-in [:temporal-field] csk/->kebab-case-keyword)
      (update-in [:start-date] parse-echo-temporal-date)
      (assoc :end-date (parse-echo-temporal-date (:stop-date rt)))
      (dissoc :stop-date)))

(defn- cmr-temporal->echo-temporal
  [rt]
  (-> rt
      (update-in [:mask] csk/->SCREAMING_SNAKE_CASE_STRING)
      (update-in [:temporal-field] csk/->SCREAMING_SNAKE_CASE_STRING)
      (update-in [:start-date] generate-echo-temporal-date)
      (assoc :stop-date (generate-echo-temporal-date (:end-date rt)))
      (dissoc :end-date)))

(defn- echo-coll-id->cmr-coll-id
  [cid]
  (when-let [{:keys [collection-ids restriction-flag temporal]} cid]
    (merge {}
           (when collection-ids
             {:entry-titles (mapv :data-set-id collection-ids)})
           (when restriction-flag
             {:access-value (set/rename-keys restriction-flag
                                             {:include-undefined-value :include-undefined})})
           (when temporal
             {:temporal
              (echo-temporal->cmr-temporal temporal)}))))

(defn cmr-coll-id->echo-coll-id
  [cid]
  (when-let [{:keys [entry-titles access-value temporal]} cid]
    (merge {}
           (when entry-titles
             {:collection-ids (for [et entry-titles]
                                {:data-set-id et})})
           (when access-value
             {:restriction-flag
              (set/rename-keys access-value
                               {:include-undefined :include-undefined-value})})
           (when temporal
             {:temporal
              (cmr-temporal->echo-temporal temporal)}))))

(defn cmr-gran-id->echo-gran-id
  [gid]
  (when-let [{:keys [access-value temporal]} gid]
    (merge {}
           (when access-value
             {:restriction-flag
              (set/rename-keys access-value
                               {:include-undefined :include-undefined-value})})
           (when temporal
             {:temporal
              (cmr-temporal->echo-temporal temporal)}))))

(defn echo-gran-id->cmr-gran-id
  [gid]
  (when-let [{:keys [restriction-flag temporal]} gid]
    (merge {}
           (when restriction-flag
             {:access-value (set/rename-keys restriction-flag
                                             {:include-undefined-value :include-undefined})})
           (when temporal
             {:temporal
              (echo-temporal->cmr-temporal temporal)}))))

(defn echo-catalog-item-identity->cmr-catalog-item-identity
  [cid]
  (some-> cid
          (update-in [:collection-identifier] echo-coll-id->cmr-coll-id)
          (update-in [:granule-identifier] echo-gran-id->cmr-gran-id)
          util/remove-nil-keys))

(defn cmr-catalog-item-identity->cmr-catalog-item-identity
  [cid]
  (some-> cid
          (update-in [:collection-identifier] cmr-coll-id->echo-coll-id)
          (update-in [:granule-identifier] cmr-gran-id->echo-gran-id)
          util/remove-nil-keys))


(defn echo-acl->cmr-acl
  "Cleans up the acl data structure to be easier to work with. See the in code comment in this
  namespace for an example."
  [acl]
  (-> acl
      :acl
      util/map-keys->kebab-case
      (set/rename-keys {:id :guid :access-control-entries :aces})
      (update-in [:aces] (partial mapv echo-ace->cmr-ace))
      (update-in [:catalog-item-identity] echo-catalog-item-identity->cmr-catalog-item-identity)
      util/remove-nil-keys))

(defn cmr-acl->echo-acl
  "Converts a cmr style acl back to the echo style. Converting echo->cmr->echo is lossy due to
  short names and version ids not being included. These are optional and don't impact enforcement
  so it's ok."
  [acl]
  (-> acl
      (update-in [:aces] (partial mapv cmr-ace->echo-ace))
      (update-in [:catalog-item-identity] cmr-catalog-item-identity->cmr-catalog-item-identity)
      (set/rename-keys {:guid :id :aces :access-control-entries})
      util/remove-nil-keys
      util/map-keys->snake_case
      (#(hash-map :acl %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This uses Prismatic Schema to describe the shape of the ACL data.

(def base-ace-schema
  {:permissions [(s/enum :order :read :create :delete :update)]})

(def group-ace-schema
  (assoc base-ace-schema
         :group-guid s/Str))

(def user-type-ace-schema
  (assoc base-ace-schema
         :user-type (s/enum :guest :registered)))

(def ace-schema
  (s/conditional
    :group-guid group-ace-schema
    :user-type user-type-ace-schema))

(def temporal-filter-schema
  {:start-date org.joda.time.DateTime
   :end-date org.joda.time.DateTime
   :mask (s/enum :disjoint :intersect :contains)
   ;; We only support acquisition as the temporal field.
   :temporal-field (s/eq :acquisition)})

(def access-value-filter-schema
  {(s/optional-key :include-undefined) s/Bool
   (s/optional-key :max-value) Number
   (s/optional-key :min-value) Number})

(def collection-identifier-schema
  {(s/optional-key :entry-titles) [s/Str]
   (s/optional-key :access-value) access-value-filter-schema
   (s/optional-key :temporal) temporal-filter-schema})

(def granule-identifier-schema
  {(s/optional-key :access-value) access-value-filter-schema
   (s/optional-key :temporal) temporal-filter-schema})

(def catalog-item-identity-schema
  {(s/optional-key :name) s/Str
   :provider-id s/Str
   (s/optional-key :collection-applicable) s/Bool
   (s/optional-key :granule-applicable) s/Bool
   (s/optional-key :collection-identifier) collection-identifier-schema
   (s/optional-key :granule-identifier) granule-identifier-schema})

(def system-object-identity-schema
  {:target s/Str})

(def provider-object-identity-schema
  {:provider-id s/Str
   :target s/Str})

(def single-instance-object-identity-schema
  {:target s/Str
   :target-guid s/Str})

(def acl-schema
  {(s/optional-key :guid) s/Str
   :aces [ace-schema]
   (s/optional-key :catalog-item-identity) catalog-item-identity-schema
   (s/optional-key :system-object-identity) system-object-identity-schema
   (s/optional-key :provider-object-identity) provider-object-identity-schema
   (s/optional-key :single-instance-object-identity) single-instance-object-identity-schema})
