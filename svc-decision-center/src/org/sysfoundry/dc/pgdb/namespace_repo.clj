(ns org.sysfoundry.dc.pgdb.namespace-repo
  (:require [org.sysfoundry.dc.namespace :as nspace]
            [org.sysfoundry.gen.comms :as comms]
            [org.sysfoundry.dc.pgdb.sqls :as s]
            [next.jdbc :as j]
            [integrant.core :as ig]
            )
  )

(defn- on-exception-response
  [e]
  (comms/failure (str "Error occured : " (.getMessage e))))

(defn- on-valid-db-connection
  [on-valid-connection-fn ds]
  (if-not (nil? ds)
    (on-valid-connection-fn)
    (comms/failure "The connection to the database is not set!")))

(defn- count-namespace-internal
  "Gets the count of namespaces matching the given criteria"
  [tx account query]
  (->> (s/count-namespaces-sql account query)
       (j/execute! tx)
       first
       comms/success))

(defn- compute-page-count
  [total page-size]
  (let [modval (mod total page-size)
        quotient (quot total page-size)]
    (if (> modval 0)
      (inc quotient)
      quotient)))


(defn- list-namespaces-internal
  "Internal list namespaces fn"
  [tx account query order-by page-info]
  (if-not (nil? page-info)
    (let [total-count-response (count-namespace-internal tx account query)]
      (comms/on-status total-count-response
                       #(let [total-count (:total_count %)
                              page-size (:paging/page-size page-info)
                              updated-page-info (assoc page-info :paging/total-pages (compute-page-count total-count page-size) :paging/total-count total-count)
                              results (->> (s/list-namespaces-sql account query order-by updated-page-info)
                                           (j/execute! tx)
                                           (comms/map-rows-to-ns "sf.dc.ns"))
                              updated-page-info-final (assoc updated-page-info :paging/current-count (count results))
                              result-map {:sf.dc.ns/namespaces results :paging/page-info updated-page-info-final}]
                          (comms/success result-map))
                       #(comms/failure %)))

    (->> (s/list-namespaces-sql account query order-by page-info)
         (j/execute! tx)
         (comms/map-rows-to-ns "sf.dc.ns")
         comms/success)))

(defn- create-namespace-internal
  [tx account ns-def]
  (let [stripped-ns-def (comms/strip-ns-from-keys ns-def)
        ns-name (:name stripped-ns-def)
        existing-ns-count (->> (s/count-namespaces-sql account [:= :name ns-name])
                               (j/execute! tx)
                               first
                               (:total_count))]
    (if-not (> existing-ns-count 0)
      (->> (s/new-namespace-sql account stripped-ns-def)
           (j/execute-one! tx)
           comms/strip-ns-from-keys
           (comms/map-keys-to-ns2 "sf.gen.comms")
           comms/success)
      (throw (Exception. (str "Namespace : " ns-name " is already defined!"))))))


(defrecord PGDBNamespaceRepo [datasource]
  nspace/NamespaceRepo
  (list-namespaces [this account query order-by page-info]
                   (try
                     (on-valid-db-connection
                      #(j/with-transaction [tx datasource {:read-only true}]
                         (list-namespaces-internal tx account query order-by page-info))
                      datasource)
                     (catch java.lang.Exception e
                       (on-exception-response e))))
  
  (create-namespaces [this account namespace-defs]
                     (try
                       (on-valid-db-connection
                        #(j/with-transaction [tx datasource]
                           (doall (map (fn [v] (create-namespace-internal tx account v)) namespace-defs)))
                        datasource
                        )
                       (catch java.lang.Exception e
                         (on-exception-response e))))
  
  (update-namespaces [this account update-map query]
                     (try
                       (on-valid-db-connection
                        #(j/with-transaction [tx datasource]
                           (->> (s/update-namespace-sql account update-map query)
                                (j/execute-one! tx)
                                comms/strip-ns-from-keys
                                (comms/map-keys-to-ns2 "sf.gen.comms")
                                comms/success)) datasource)
                       (catch Exception e
                         (on-exception-response e))))
  
  (delete-namespaces [this account query]
                     (try
                       (on-valid-db-connection
                        #(j/with-transaction [tx datasource]
                           (let [search-result (list-namespaces-internal tx account query nil nil)]
                             (comms/on-status search-result
                                              (fn [success-data]
                                                (let [active-namespaces (filter (fn [ns-m]
                                                                                  (:sf.dc.ns/active ns-m)) success-data)]
                                                  (if (> (count active-namespaces) 0)
                                                    (comms/failure
                                                     #:sf.gen.comms{:error :sf.dc.ns/attempt-to-delete-active-ns :error-data active-namespaces})
                                                    (->> (s/delete-namespace-sql account query)
                                                         (j/execute-one! tx)
                                                         comms/strip-ns-from-keys
                                                         (comms/map-keys-to-ns2 "sf.gen.comms")
                                                         comms/success))))
                                              (fn [failure-data]
                                                (comms/failure failure-data))))) datasource)
                       (catch Exception e
                         (on-exception-response e))))
  )

(defmethod ig/init-key ::pgdb-namespace-repo [_ config]
  (map->PGDBNamespaceRepo config)
  )