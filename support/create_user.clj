;;; run with lein exec create_user.clj

(use '[leiningen.exec :only (deps)])
(deps '[[com.oracle/ojdbc6 "11.2.0.3"]
        [org.clojure/java.jdbc "0.3.3"]]
      :repositories {"releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"})
        
(require '[clojure.java.jdbc :as j])

(def db {:classname "oracle.jdbc.driver.OracleDriver"
         :subprotocol "oracle"
         :subname "thin:@localhost:1521:orcl"
         :user "sys as sysdba"
         :password "oracle"})

;; TODO - need to decide which tablespaces to use
; "create user %%CMR_USER%%
; profile default
; identified by %%CMR_PASSWORD%%
; default tablespace %%DATA_TABLESPACE%%
; temporary tablespace %%TEMP_TABLESPACE%%
; quota unlimited on %%DATA_TABLESPACE%%
; quota unlimited on %%INDEX_TABLESPACE%%
; account unlock"

(def create-user-sql "create user %%CMR_USER%%
                     profile default
                     identified by %%CMR_PASSWORD%%
                     account unlock")

(def grant-sql ["grant create session to %%CMR_USER%%"
                "grant create table to %%CMR_USER%%"
                "grant create sequence to %%CMR_USER%%"
                "grant create view to %%CMR_USER%%"
                "grant create procedure to %%CMR_USER%%"])

(def replacements {"CMR_USER" "METADATA_DB"
                   "CMR_PASSWORD" "METADATA_DB"})

(defn replace-values
  ""
  [template key-values]
  (reduce (fn [temp [key,val]]
            (clojure.string/replace temp (str "%%" key "%%") val)) 
          template 
          key-values))

(j/db-do-commands db (replace-values create-user-sql replacements))

(doseq [sql grant-sql]
  (j/db-do-commands db (replace-values sql replacements)))