(ns dvlopt.linux.i2c.smbus

  "The SMBus protocol is more or less a subset of I2C. Quite often, SMBus operations can be carried
   out on an I2C bus. In consequence, the Linux kernel provides SMBus operation defined in the standard.

   Those operations performs common interactions. Single byte can be exchanged, as well as words (2 bytes)
   and blocks (at most 32 bytes at a time). The term \"command\" refers to what is also called a \"register\"."

  {:author "Adam Helinski"}

  (:import (io.dvlopt.linux.i2c I2CBus
                                SMBus
                                SMBus$Block)))




;;;;;;;;;;


(defn quick-read

  "Sends a read message without any content."

  [^I2CBus bus]

  (.quick (.-smbus bus)
          false))




(defn quick-write

  "Sends a write message without any content."

  [^I2CBus bus]

  (.quick (.-smbus bus)
          true))




(defn read-byte-directly

  "Reads a single byte."

  [^I2CBus bus]

  (.readByteDirectly (.-smbus bus)))




(defn write-byte-directly

  "Writes a single byte."

  [^I2CBus bus b]

  (.writeByteDirectly (.-smbus bus)
                      b))




(defn read-byte

  "Reads a byte after specifying a command."

  [^I2CBus bus command]

  (.readByte (.-smbus bus)
             command))




(defn write-byte

  "Write a byte after specifying a command."

  [^I2CBus bus command b]

  (.writeByte (.-smbus bus)
              command
              b))




(defn read-word

  "Read a word after specifying a command."

  [^I2CBus bus command]

  (.readWord (.-smbus bus)
             command))




(defn write-word

  "Writes a word after specifying a command."

  [^I2CBus bus command w]

  (.writeWord (.-smbus bus)
              command
              w))




(defn- -block->vec

  ;; Converts an SMBus$Block into a vector.

  [^SMBus$Block block length]

  (into []
        (for [i length]
          (.get block
                i))))




(defn- -seq->block

  ;; Converts a sequable into an SMBus$Block.

  ^SMBus$Block

  [sq]

  (let [block (SMBus$Block.)]
     (doseq [[index b] (partition 2
                                  (interleave (range)
                                              sq))]
       (.set block
             index
             b))
     block))




(defn read-block

  "Reads a block after specifying a command."

  [^I2CBus bus command]

  (let [block (SMBus$Block.)]
    (-block->vec block
                 (.readBlock (.-smbus bus)
                             command
                             block))))




(defn write-block

  "Writes a block after specifying a command as well as the number of bytes in the block."

  [^I2CBus bus command bs]

  (.writeBlock (.-smbus bus)
               command
               (-seq->block bs))
  nil)




(defn read-i2c-block

  "Reads a block of chosen length after specifying a command.
  
   Not standard but often encountered and supported."

  [^I2CBus bus command length]

  (let [block (SMBus$Block.)]
    (.readI2CBlock (.-smbus bus)
                   command
                   block
                   length)
    (-block->vec block
                 length)))




(defn write-i2c-block

  "Writes a block after specifying a command.
  
   Unlike `write-block`, does not send a byte count.
  
   Not standard but often encountered and supported."

  [^I2CBus bus command bs]

  (.writeI2CBlock (.-smbus bus)
                  command
                  (-seq->block bs))
  nil)




(defn process-call

  "Performs a simple process call by writing a word acting as an argument and then reading a word acting as the result."

  [^I2CBus bus command w]

  (.processCall (.-smbus bus)
                command
                w))




(defn block-process-call

  "Performs a multi-byte process call by writing a block acting as an argument and then reading a block acting as the result."

  [^I2CBus bus command bs]

  (let [block (-seq->block bs)]
    (-block->vec block
                 (.blockProcessCall (.-smbus bus)
                                    command
                                    block))))
