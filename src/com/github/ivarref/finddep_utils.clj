(ns com.github.ivarref.finddep-utils
  (:require [clojure.java.io :as jio]))

(defn get-opt [opts kw default]
  (assert (map? opts))
  (assert (keyword? kw))
  (let [res (or
              (get opts kw)
              (get opts (name kw))
              (get opts (symbol kw))
              default)]
    (if (and (= res :exit)
             (= default :exit))
      (binding [*out* *err*]
        (println (str "ERROR: You must specify keyword " kw))
        (System/exit 1))
      res)))

(defn require-deps-edn! []
  (when (not (.exists (jio/file "deps.edn")))
    (binding [*out* *err*]
      (println "Error. Not a tools.deps project. Missing deps.edn"))
    (System/exit 1)))