(ns org.sysfoundry.oas.v3.schema
  "This namespace provides the functions necessary to convert a OAS V3 schema data structure to a prismatic schema spec"
  (:require [schema.core :as s]
            [clojure.string :as str]
            )
  )

(declare obj->spec type->spec ref->spec)

(def basic-types ["string"
                  "number"
                  "integer"
                  "boolean"]
  )

(defn basic-type? [type-str]
  (some #(= % type-str) basic-types)
  )

(defn array-type? [type-str]
  (= "array" type-str)
  )

(defn object-type? [type-str]
  (= "object" type-str))


(defn array->spec [val api-model]
  (let [type (:type val)
        items (:items val)
        item-type (:type items)
        ref-type (:$ref items)
        ]
    (if-not (array-type? type)
      (throw (ex-info "only 'array' types are supported in this fn" {:data val}))
      (if (and (map? items) (empty? items))
      ;means it can match any arbitrary type of data
        [s/Any]
        (cond
          (basic-type? item-type) [(type->spec items api-model)]
          (array-type? item-type) [(array->spec items api-model)]
          (object-type? item-type) [(obj->spec items api-model)]
          (not (nil? ref-type)) [(ref->spec ref-type api-model)]
          )))
    )
  )


(defn obj->spec
  [val api-model]
  (let [type (:type val)
        properties (:properties val)
        req-properties (map #(keyword %)(:required val))]
    (if-not (= type "object")
      (let [ref-type (:$ref val)]
        (if-not (nil? ref-type)
          (ref->spec ref-type api-model)
          (throw (ex-info "only 'object' types & '$ref's are supported in this fn" {:data val}))
          )
        )
      
      (if (nil? properties)
        {s/Any s/Any} ;;simply a map if the properties are empty. We do not worry about the contents of the map
        (->> properties
             (map #(let [property (first %)
                         val (second %)
                         required? (some #{property} req-properties)]
                     (if required?
                       {property (type->spec val api-model)}
                       {(s/optional-key property) (type->spec val api-model)})))
             (into {}))))))

(defn type->spec [val api-model]
  (let [type (:type val)
        ref-type (:$ref val)]
    (if-not (nil? type)
      (cond
        (= type "string") s/Str
        (= type "number") s/Num
        (= type "integer") s/Int
        (= type "boolean") s/Bool
        (= type "array") (array->spec val api-model)
        (= type "object") (obj->spec val api-model)
        :else (throw (ex-info (str "unsupported type : " type) {:data val})))
      (if-not (nil? ref-type)
        (ref->spec ref-type api-model)
        (throw (ex-info (str "Unprocessable type info " val) {:data val}))
        )
      )
    ))

(defn refpath->vec [ref-path-str]
  (if-not (str/starts-with? ref-path-str "#")
    (throw (ex-info (str ref-path-str " is not a valid internal reference") {:ref-path ref-path-str}))
    (->> 
     (str/split ref-path-str #"/")
     rest ;ignore the # in the beginning
     (map #(keyword %)) ;convert to keywords
     vec ;convert to a vector
     )
    )
  )

(defn ref->spec [ref-path api-model]
  (let [ref-path-vec (refpath->vec ref-path)
        comp-obj-def (get-in api-model ref-path-vec :invalid-path)
        ]
    (if (= :invalid-path comp-obj-def)
      (throw (ex-info (str "$ref path : " ref-path-vec " -> [" ref-path "], is not pointing to any def under 'components'")
                      {:path ref-path}
                      ))
      (type->spec comp-obj-def api-model)
      )
    )
  )



