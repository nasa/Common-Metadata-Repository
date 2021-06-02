(ns cmr.transmit.access-control
  "This contains functions for interacting with the access control API."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- groups-url
  [conn]
  (format "%s/groups" (conn/root-url conn)))

(defn- reindex-groups-url
  [conn]
  (format "%s/reindex-groups" (conn/root-url conn)))

(defn- group-url
  [conn group-id]
  (str (groups-url conn) "/" group-id))

(defn members-url
  [conn group-id]
  (str (group-url conn group-id) "/members"))

(defn current-sids-url
  [conn]
  (format "%s/current-sids" (conn/root-url conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn reset
  "Sends a request to call the reset endpoint of the given app.
   * :raw - set to true to indicate the raw response should be returned. See
   cmr.transmit.http-helper for more info. Default false."
  ([context]
   (reset context nil))
  ([context {:keys [raw? bootstrap-data?]}]
   (h/request context :access-control
              {:url-fn cmr.transmit.http-helper/reset-url
               :http-options {:query-params {:bootstrap_data bootstrap-data?}}
               :method :post
               :raw? raw?
               :use-system-token? true})))

(defn reindex-groups
  "Sends a request to call the reindex groups
   * :raw - set to true to indicate the raw response should be returned. See
   cmr.transmit.http-helper for more info. Default false."
  ([context]
   (reindex-groups context nil))
  ([context {:keys [raw?]}]
   (h/request context :access-control
              {:url-fn reindex-groups-url
               :method :post
               :raw? raw?
               :use-system-token? true})))

(h/defcacheclearer clear-cache :access-control)

; Group CRUD functions
(h/defcreator create-group :access-control groups-url)
(h/defsearcher search-for-groups :access-control groups-url)
(h/defupdater update-group :access-control group-url)
(h/defdestroyer delete-group :access-control group-url)
(h/defgetter get-group :access-control group-url)

(defn- modify-members
  "Helper functions for adding or removing members"
  [context action concept-id members {:keys [raw? token http-options]}]
  (let [token (or token (:token context))
        headers (when token {config/token-header token})]
    (h/request context :access-control
               {:url-fn #(members-url % concept-id)
                :method action
                :raw? raw?
                :http-options (merge {:body (json/generate-string members)
                                      :content-type :json
                                      :headers headers
                                      :accept :json}
                                     http-options)})))

(defn add-members
  "Adds members to the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id members]
   (add-members context concept-id members nil))
  ([context concept-id members options]
   (modify-members context :post concept-id members options)))

(defn remove-members
  "Removes specified members from the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id members]
   (remove-members context concept-id members nil))
  ([context concept-id members options]
   (modify-members context :delete concept-id members options)))

(defn get-members
  "Gets a list of the members in the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-members context concept-id nil))
  ([context concept-id {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :access-control
                {:url-fn #(members-url % concept-id)
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json}
                                      http-options)}))))

(defn get-current-sids
  "Gets a list of the SIDs for the specified token (used for assigning permissions via ACLs)."
  [context user-token]
  (let [token (:token context)
        headers (when token {config/token-header token})]
    (h/request context :access-control
               {:url-fn current-sids-url
                :method :get
                :http-options {:query-params {:user-token user-token}
                               :headers headers
                               :accept :json}})))

;;; ACL Functions

(def acl-type->acl-key
  "A map of the acl object identity type to the field within the acl that stores the object."
  {:catalog-item :catalog-item-identity
   :system-object :system-identity
   :provider-object :provider-identity
   :single-instance-object :single-instance-identity})

(defn acl-root-url
  "Returns the URL of the ACL API root."
  [ctx]
  (str (conn/root-url ctx) "/acls/"))

(defn acl-concept-id-url
  "Returns the URL of the ACL API root."
  [ctx concept-id]
  (str (conn/root-url ctx) "/acls/" concept-id))

(defn acl-search-post-url
  "Returns ACL search URL for POST requests."
  [ctx]
  (str (conn/root-url ctx) "/acls/search"))

(defn acl-permission-url
  [ctx]
  (str (conn/root-url ctx) "/permissions/"))

(defn- reindex-acls-url
  [conn]
  (format "%s/reindex-acls" (conn/root-url conn)))

(defn reindex-acls
  "Sends a request to call the reindex acls
   * :raw - set to true to indicate the raw response should be returned. See
   cmr.transmit.http-helper for more info. Default false."
  ([context]
   (reindex-acls context nil))
  ([context {:keys [raw?]}]
   (h/request context :access-control
              {:url-fn reindex-acls-url
               :method :post
               :raw? raw?
               :use-system-token? true})))

(h/defcreator create-acl :access-control acl-root-url)
(h/defupdater update-acl :access-control acl-concept-id-url)

(h/defsearcher search-for-acls-get :access-control acl-root-url)

(h/defsearcher search-for-acls* :access-control acl-search-post-url)

(defn search-for-acls
  "Search for ACLs."
  ([context params]
   (search-for-acls context params nil))
  ([context params options]
   (search-for-acls* context params (merge options {:method :post}))))

(h/defdestroyer delete-acl :access-control acl-concept-id-url)
(h/defgetter get-acl :access-control acl-concept-id-url)

(h/defsearcher get-permissions :access-control acl-permission-url)
;;; Misc. Functions

;; Defines health check function
(h/defhealther get-access-control-health :access-control {:timeout-secs 2})
