(ns com.github.ivarref.finddep-utils
  (:require [clojure.java.io :as jio]))

(defn get-opt [opts kw default]
  (assert (map? opts))
  (assert (keyword? kw))
  (let [res (cond (contains? opts kw)
                  (get opts kw)

                  (contains? opts (name kw))
                  (get opts (name kw))

                  (contains? opts (symbol kw))
                  (get opts (symbol kw))

                  :else
                  default)]
    (if (and (= res :exit)
             (= default :exit))
      (binding [*out* *err*]
        (println (str "ERROR: You must specify keyword " kw))
        (System/exit 1))
      res)))

(defn get-opts [opts kws default]
  (assert (map? opts))
  (assert (vector kws))
  (let [res (reduce
              (fn [o kw]
                (assert (keyword? kw))
                (let [res (or
                            (get opts kw)
                            (get opts (name kw))
                            (get opts (symbol kw))
                            ::missing)]
                  (if (= res ::missing)
                    o
                    (reduced res))))
              default
              kws)]
    (if (and (= res :exit)
             (= default :exit))
      (binding [*out* *err*]
        (println (str "ERROR: You must specify one of keywords: " (str kws)))
        (System/exit 1))
      res)))

(defn require-deps-edn! []
  (when (not (.exists (jio/file "deps.edn")))
    (binding [*out* *err*]
      (println "Error. Not a tools.deps project. Missing deps.edn"))
    (System/exit 1)))