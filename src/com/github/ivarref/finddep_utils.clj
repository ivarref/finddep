(ns com.github.ivarref.finddep-utils
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.string :as str])
  (:import (java.util Map)))

(defn- esc [c s]
  (str "\u001B[" c
       s
       "\u001B[0m"))

(defn- println-red [{:keys [no-color? println!] :as ctx} & s]
  (println!
    (if no-color?
      (str/join " " s)
      (esc "31m" (str/join " " s)))))


(defn- read-deps-edn [{:keys [exit! deps-path] :as ctx}]
  (if-not (.exists (jio/file deps-path))
    (do
      (println-red ctx "File" deps-path "does not exist")
      (exit! 1))
    (edn/read-string (slurp deps-path))))

(defn- get-alias-type [deps-edn alias]
  (let [alias' (if (string? alias)
                 (keyword alias)
                 alias)]
    (if-let [alias'' (get-in deps-edn [:aliases alias'])]
      (when (map? alias'')
        (cond (and (contains? alias'' :extra-paths)
                   (contains? alias'' :exec-fn))
              :extra

              (contains? alias'' :extra-deps)
              :extra

              (contains? alias'' :override-deps)
              :tool

              (contains? alias'' :default-deps)
              :extra

              (contains? alias'' :deps)
              :tool

              (contains? alias'' :replace-deps)
              :tool

              :else
              nil))
      nil)))

(defn- get-all-aliases [{:keys [exit!] :as ctx}]
  (let [deps-edn (read-deps-edn ctx)
        aliases' (->> (keys (:aliases deps-edn))
                      (filterv #(some? (get-alias-type deps-edn %)))
                      (mapv name)
                      (sort)
                      (vec))]
    (if (= 0 (count aliases'))
      (do
        (println-red ctx "No aliases defined in deps.edn")
        (exit! 1))
      aliases')))

(def default-ctx
  {:no-color? (.containsKey ^Map (System/getenv) "NO_COLOR")
   :deps-path "deps.edn"
   :println!  println
   :verbose?  (and (.containsKey ^Map (System/getenv) "FINDDEP_VERB")
                   (= "true" (System/getenv "FINDDEP_VERB")))
   :exit!     (fn [code]
                (System/exit code))})

(defn get-alias-type-2 [alias]
  (get-alias-type (read-deps-edn default-ctx)
                  alias))

(defn expand-aliases [aliases]
  (cond (= aliases :all)
        (get-all-aliases default-ctx)

        (= aliases 'all)
        (get-all-aliases default-ctx)

        :else
        aliases))

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