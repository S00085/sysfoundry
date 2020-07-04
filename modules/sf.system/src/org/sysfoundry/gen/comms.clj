(ns org.sysfoundry.gen.comms
  "This namespace holds the functions meant to work with the comms datastructure for the sysfoundry platform"
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]))

(s/def ::out (s/keys :req [::status ::data]))

(defn success?
  "Determines whether the given input has success information. Returns True if it has :status is :status/success. 
   Returns False if it has :status otherwise"
  [m]
  (= ::success (::status m)))

(defn failure?
  "Determines whether the given input has failure information. Returns True if it has :status is :status/failure. 
   Returns False if it has :status otherwise"
  [m]
  (= ::failure (::status m)))

(defn on-status
  "Invokes the on-success-fn in the case of :status being :status/success. 
   Else it invokes the provided else-fn. The invoked function is passed the necessary datastructure present in the :data key in the provided input"
  ([m on-success-fn else-fn]
   (if (s/valid? ::out m)
     (if (= ::success (::status m))
       (on-success-fn (::data m))
       (else-fn (::data m)))
     (else-fn ::invalid-schema)))
  ([m on-success-fn on-failure-fn else-fn]
   (if (s/valid? ::out m)
     (if (= ::success (::status m))
       (on-success-fn (::data m))
       (if (= ::failure (::status m))
         (on-failure-fn (::data m))
         (else-fn ::unsupported-status)))
     (else-fn ::invalid-schema))))

(defn success
  "Wraps the given data in the standard comms success datastructure"
  [data]
  {::status ::success ::data data})

(defn failure
  "Wraps the given data in the standard comms failure datastructure"
  [data]
  {::status ::failure ::data data})

(defn map-keys-to-ns
  [m ns-name]
  (->> m
       keys
       (map #(name %))
       (map #(keyword ns-name %))
       (zipmap (keys m))
       (set/rename-keys m)))

(defn map-keys-to-ns2
  [ns-name m]
  (map-keys-to-ns m ns-name))

(defn map-rows-to-ns
  [ns-name rows]
  (map #(map-keys-to-ns % ns-name) rows))

(defn strip-ns-from-keyword
  [k]
  (-> k name keyword))

(defn strip-ns-from-keys
  [m]
  (->> m
       (map #(do {(strip-ns-from-keyword (first %)) (first (next %))}))
       (apply conj)))

(defn map-matching-items
  [items item-m]
  (if (vector? items)
    (vec (map #(if (contains? item-m %)
                 (get item-m %)
                 %) items))
    items))

(defn map-vec-items
  [items-vec item-map-fn]
  (if (vector? items-vec)
    (loop [rem-items (rest items-vec)
           curr-item (first items-vec)
           curr-vec (transient [])
           prc-stack []
           rem-stack []]
      (if-not (nil? curr-item)
        (if (vector? curr-item)
          (recur (rest curr-item)
                 (first curr-item)
                 (transient [])
                 (conj prc-stack curr-vec)
                 (conj rem-stack rem-items))
          (recur (rest rem-items)
                 (first rem-items)
                 (conj! curr-vec (item-map-fn curr-item))
                 prc-stack
                 rem-stack))
        (if-not (empty? rem-stack)
          (recur (rest (last rem-stack))
                 (first (last rem-stack))
                 (conj! (last prc-stack) (persistent! curr-vec))
                 (pop prc-stack)
                 (pop rem-stack))
          (persistent! curr-vec))))
    items-vec))