(ns com.github.ivarref.find-dep-test
  (:require [clj-commons.pretty.repl]
            [clojure.java.io :as io]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.test :as t]
            [com.github.ivarref.finddep :as fd]))

(clj-commons.pretty.repl/install-pretty-exceptions)

(t/deftest basic
  (let [libs (fd/get-libs [] {:deps {'s3-wagon-private/s3-wagon-private {:mvn/version "1.3.5"}}})]
    (t/is (= #{'com.fasterxml.jackson.core/jackson-annotations
               'com.fasterxml.jackson.core/jackson-core
               'com.fasterxml.jackson.core/jackson-databind
               'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor}
             (fd/find-needles libs 'jackson)))
    (t/is (= #{'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor}
             (fd/find-needles libs 'jackson-dataformat)))
    (let [libs (fd/libs-with-needles libs (fd/find-needles libs 'jackson-dataformat))]
      (t/is (= 1 (fd/depth libs 'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor)))
      (t/is (= 2 (fd/depth libs 'com.amazonaws/aws-java-sdk-core)))
      (t/is (= 3 (fd/depth libs 'com.amazonaws/aws-java-sdk-kms)))
      (t/is (= 3 (fd/depth libs 'com.amazonaws/aws-java-sdk-sts)))
      (t/is (= 4 (fd/depth libs 'com.amazonaws/aws-java-sdk-s3)))
      (t/is (= 5 (fd/depth libs 's3-wagon-private/s3-wagon-private))))))

(defn spit2 [f s]
  (when (not= (slurp f)
              s)
    (spit f s))
  s)

(t/deftest can-print-git-tag
  (let [libs (fd/get-libs [] {:deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}})]
    (t/is (= (slurp "test/output_git_tag.txt")
             (with-out-str (fd/find {:name "" :libs libs}))))))

(defn local-root-content []
  (let [s (slurp "test/local_root.txt")]
    (str/replace s
                 "/Users/ire/code/finddep"
                 (.getAbsolutePath (.getParentFile (.getAbsoluteFile (io/file ".")))))))

(t/deftest can-print-local-root
  (t/is (= (local-root-content)
           (with-out-str
             (fd/find
               {:name ""
                :libs (fd/get-libs [] {:deps {'local/root {:local/root (.getAbsolutePath (jio/file "."))}}})})))))

(t/deftest alias-support-1
  (t/is (= (slurp "test/no_alias.txt")
           (with-out-str
             (fd/find
               {:name ""
                :libs (fd/get-libs [] {:deps    {'org.slf4j/slf4j-nop {:mvn/version "2.0.7"}}
                                       :aliases {:test {:extra-deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}}}})})))))

(t/deftest alias-support-2
  (t/is (= (slurp "test/alias.txt")
           (with-out-str
             (fd/find
               {:name ""
                :libs (fd/get-libs [:test] {:deps    {'org.slf4j/slf4j-nop {:mvn/version "2.0.7"}}
                                            :aliases {:test {:extra-deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}}}})})))))

(t/deftest git-tag-is-nil
  (t/is (= (slurp "test/output_git_tag_is_nil.txt")
           (with-out-str
             (fd/find
               {:name ""
                :libs (fd/get-libs [] {:deps {'io.github.joakimen/fzf.clj {:git/sha "2063e0f6e1a7f78b5869ef1424e04e21ec46e1eb"}}})})))))
