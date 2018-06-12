(ns dvlopt.linux.i2c

  "The Linux kernel provides a standard interface for performing I2C operations.
  
   This library exposes this interface in a clojure idiomatic way.

   Each IO operation might throw is something fails."

  {:author "Adam Helinski"}

  (:refer-clojure :exclude [read])
  (:require [dvlopt.void :as void])
  (:import (io.dvlopt.linux.i2c I2CBuffer
                                I2CBus
                                I2CMessage
                                I2CFlag
                                I2CFlags
                                I2CFunctionalities
                                I2CFunctionality
                                I2CTransaction)))



;;;;;;;;;;


(def defaults

  "Defaults values for options used throughout this library."

  {::10-bit?        false
   ::force?         false
   ::ignore-nak?    false
   ::no-read-ack?   false
   ::no-start?      false
   ::revise-rw-bit? false
   ::slave-address  0})




;;;;;;;;;;


(defn bus

  "Opens an I2C bus by providing the number of the bus or a direct path.
  
   
   Ex. (bus \"/dev/i2c-1\")"

  [bus-path]

  (if (string? bus-path)
    (I2CBus. ^String bus-path)
    (I2CBus. ^int    bus-path)))




(defn close

  "Closes an I2C bus."

  [^I2CBus bus]

  (.close bus))




(defn select-slave

  "Selects an I2C slave device.

   Affects every IO operations besides transactions where the slave address is given for each message.


   Ex. (select-slave some-bus
                     0x42
                     {::10-bit? false
                      ::force?  false})"

  ([bus slave-address]

   (select-slave bus
                 slave-address
                 nil))


  ([^I2CBus bus slave-address slave-options]

   (.selectSlave bus
                 slave-address
                 (void/obtain ::force?
                              slave-options
                              defaults)
                 (void/obtain ::10-bit?
                              slave-options
                              defaults))))




(defn set-retries

  "Sets the number of retries when communication fails.

   Does not always produce an effect depending on the underlying driver."

  [^I2CBus bus retries]

  (.setRetries bus
               retries))




(defn set-timeout

  "Sets the timeout in milliseconds for slave responses.

   Does not always produce an effect depending on the underlying driver."

  [^I2CBus bus timeout-ms]

  (.setTimeout bus
               timeout-ms))




(defn- -buffer->vec

  ;; Converts an I2C buffer to a vector.

  [^I2CBuffer buffer]

  (into []
        (for [i (range (.-length buffer))]
          (.get buffer
                i))))




(defn- -seq->buffer

  ;; Converts a seqable into an I2CBuffer.

  ^I2CBuffer

  [sq]

  (let [buffer (I2CBuffer. (count sq))]
    (doseq [[index b] (partition 2
                                 (interleave (range)
                                             sq))]
      (.set buffer
            index
            b))
    buffer))




(defn- -flags

  ;; Given flags, produces an I2CFlags object.

  ^I2CFlags

  [flags]

  (let [i2c-flags (I2CFlags.)]
    (when (contains? flags
                     ::read)
      (.set i2c-flags
            I2CFlag/READ))
    (doseq [[flag i2c-flag] [[::10-bit?        I2CFlag/TEN_BIT_ADDRESSING]
                             [::ignore-nak?    I2CFlag/IGNORE_NAK]
                             [::no-read-ack?   I2CFlag/NO_READ_ACK]
                             [::no-start?      I2CFlag/NO_START]
                             [::revise-rw-bit? I2CFlag/REVISE_RW_BIT]]]
      (when (void/obtain flag
                         flags
                         defaults)
        (.set i2c-flags
              i2c-flag)))
    i2c-flags))




(defn transaction

  "A transaction represents a sequence of messages, reads and writes, meant to be carried out without interruption.

  Not every device supports this feature, or sometimes only supports 1 message per transaction with defeats their purpose.
  
  Each message specifies if it is a read or a write and consists of options :

    ::10-bit?         Should the 10-bit addressing mode be used ?
    ::ignore-nak?     Should \"not-acknowledge\" be ignored ?
    ::no-read-ack?    Should read-acks be ignored ?
    ::no-start?       Should not issue any more START/address after the initial one.
    ::revise-wr-bit?  Should send a read flag for writes and vice-versa (for broken slave) ?
    ::slave-address   Which slave.
    ::tag             Any value associated with the message, important for reads (the number of the message
                      by default).

  After the transaction is carried out, a map of tag -> bytes is returned for reads.
  
  
  Ex. (transaction some-bus
                   [{::slave-address 0x42
                     ::write         [24 1 2 3]}
                    {::slave-address 0x42
                     ::read          3
                     ::tag           :my-read}])

      => {:my-read [...]}"

  [^I2CBus bus messages]

  (let [length          (count messages)
        i2c-transaction (I2CTransaction. length)
        tag->buffer     (reduce (fn prepare-message [tag->buffer [i ^I2CMessage i2c-message message]]
                                  (let [[buffer
                                         tag]   (if (contains? message
                                                               ::read)
                                                  [(I2CBuffer. (::read message))
                                                   (get message
                                                        ::tag
                                                        i)]
                                                  [(-seq->buffer (::write message))
                                                   nil])]
                                    (.setBuffer i2c-message
                                                buffer)
                                    (.setAddress i2c-message
                                                 (void/obtain ::slave-address
                                                              message
                                                              defaults))
                                    (.setFlags i2c-message
                                               (-flags message))
                                    (if (nil? tag)
                                      tag->buffer
                                      (assoc tag->buffer
                                             tag
                                             buffer))))
                                {}
                                (partition 3
                                           (interleave (range)
                                                       (for [i (range length)]
                                                         (.getMessage i2c-transaction
                                                                      i))
                                                       messages)))]
    (.doTransaction bus
                    i2c-transaction)
    (reduce-kv (fn convert-buffer [tag->vec tag buffer]
                 (assoc tag->vec
                        tag
                        (-buffer->vec buffer)))
               {}
               tag->buffer)))




(defn read

  "Reads an arbitrary amount of bytes."

  [^I2CBus bus length]

  (let [buffer (I2CBuffer. length)]
    (.read bus
           buffer)
    (-buffer->vec buffer)))




(defn write

  "Writes a sequence of bytes."

  [^I2CBus bus bs]

  (.write bus
          (-seq->buffer bs))
  nil)
