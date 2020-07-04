(ns org.sysfoundry.dc.pgdb.sqls
  "This namespace houses the functions required to formulate the sqls for interacting with postgres db"
  (:require [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [honeysql.format :as f]
            [org.sysfoundry.gen.comms :as comms]))

(def namespace-key :rms/namespace)
(def account-key :ac_id)

(def ns-def-replacements
  {:sf.dc.ns/name :name
   :sf.dc.ns/id :id
   :sf.dc.ns/doc :doc
   :sf.dc.ns/active :active
   :sf.dc.ns/ac_id :ac_id})

(defn- ns-key-replace-fn
  [item]
  (if (contains? ns-def-replacements item)
    (get ns-def-replacements item)
    item))

(defn orderby-sql
  [m order-by]
  (if-not (nil? order-by)
    (h/order-by m order-by)
    m))

(defn paging-sql
  [m page-info]
  (if-not (nil? page-info)
    (let [page-size (:paging/page-size page-info)
          page-num (:paging/page-num page-info)
          offset (* page-size (max 0 (dec page-num)))]
      (-> m
          (h/limit page-size)
          (h/offset offset)))
    m))

(defn select-sql
  [account table attributes order-by page-info]
  (-> (apply h/select attributes)
      (h/from table)
      (h/where [:= account-key account])
      (orderby-sql order-by)
      (paging-sql page-info)))


(defn insert-sql
  [account table attributes]
  (apply h/columns
         (h/insert-into table)
         attributes))

(defn update-sql
  [account table att-map]
  (-> (h/update table)
      (h/sset att-map)
      (h/where [:= account-key account])))

(defn delete-sql
  [account table]
  (-> (h/delete-from table)
      (h/where [:= account-key account])))


(defn fmt
  [in]
  (f/format in :namespace-as-table? true))

(defn delete-namespace-sql
  [account query]
  (let [alt-query (comms/map-vec-items query ns-key-replace-fn)]
    (-> (delete-sql account namespace-key)
        (h/merge-where alt-query)
        fmt)))

(defn update-namespace-sql
  [account m query]
  (let [alt-query (comms/map-vec-items query ns-key-replace-fn)]
    (-> (update-sql account namespace-key m)
        (h/merge-where alt-query)
        fmt)))

(defn list-namespaces-sql
  [account query order-by page-info]
  (let [alt-query (comms/map-vec-items query ns-key-replace-fn)
        alt-order-by (comms/map-vec-items order-by ns-key-replace-fn)]
    (-> (select-sql account namespace-key [:*] alt-order-by page-info)
        (h/merge-where alt-query)
        fmt)))

(defn count-namespaces-sql
  [account query]
  (let [alt-query (comms/map-vec-items query ns-key-replace-fn)]
    (-> (select-sql account namespace-key [[:%count.* :total_count]] nil nil)
        (h/merge-where alt-query)
        fmt)))


(defn new-namespace-sql
  "Generates the sql for new namespace. Takes the account and the namespace map as input."
  [account m]
  (let [att-keys (->> m keys (cons account-key) vec)
        att-vals (->> m vals (cons account) vec)]
    (-> (insert-sql account namespace-key att-keys)
        (h/values [att-vals])
        fmt)))

(defn new-namespaces-sql
  [account ns-defs]
  (let [att-keys (->> (first ns-defs)
                      keys
                      (cons account-key)
                      vec)
        att-vals-ls (map #(->> %
                               vals
                               (cons account)
                               vec) ns-defs)]
    (-> (insert-sql account namespace-key att-keys)
        (h/values att-vals-ls)
        fmt)))

(comment
  (def rule-def-example {:sf.dc.ns/name "test"
                         :sf.dc.rule/name "rule-name"
                         :sf.dc.rule/active true
                         :sf.dc.rule/doc "Rule documentation"
                         :sf.dc.rule/definition "{}"}))

