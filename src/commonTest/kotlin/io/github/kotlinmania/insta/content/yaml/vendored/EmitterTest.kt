// port-lint: tests content/yaml/vendored/emitter.rs
package io.github.kotlinmania.insta.content.yaml.vendored

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmitterTest {
    @Test
    fun testEmitSimple() {
        val s = """
# comment
a0 bb: val
a1:
    b1: 4
    b2: d
a2: 4 # i'm comment
a3: [1, 2, 3]
a4:
    - [a1, a2]
    - 2
"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        val docsNew = YamlLoader.loadFromStr(writer.toString())
        assertNotNull(docsNew)
        val docNew = docsNew[0]

        assertEquals(doc, docNew)
    }

    @Test
    fun testEmitComplex() {
        val s = """
catalogue:
  product: &coffee   { name: Coffee,    price: 2.5  ,  unit: 1l  }
  product: &cookies  { name: Cookies!,  price: 3.40 ,  unit: 400g}

products:
  *coffee:
    amount: 4
  *cookies:
    amount: 4
  [1,2,3,4]:
    array key
  2.4:
    real key
  true:
    bool key
  {}:
    empty hash key
"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        val docsNew = YamlLoader.loadFromStr(writer.toString())
        assertNotNull(docsNew)
        val docNew = docsNew[0]

        assertEquals(doc, docNew)
    }

    @Test
    fun testEmitAvoidQuotes() {
        val s = """---
a7: 你好
boolean: "true"
boolean2: "false"
date: 2014-12-31
empty_string: ""
empty_string1: " "
empty_string2: "    a"
empty_string3: "    a "
exp: "12e7"
field: ":"
field2: "{"
field3: "\\"
field4: "\n"
field5: "can't avoid quote"
float: "2.6"
int: "4"
nullable: "null"
nullable2: "~"
products:
  "*coffee":
    amount: 4
  "*cookies":
    amount: 4
  ".milk":
    amount: 1
  "2.4": real key
  "[1,2,3,4]": array key
  "true": bool key
  "{}": empty hash key
x: test
y: avoid quoting here
z: string with spaces"""

        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(s, writer.toString())
    }

    @Test
    fun emitQuotedBools() {
        val input = """---
string0: yes
string1: no
string2: "true"
string3: "false"
string4: "~"
null0: ~
[true, false]: real_bools
[True, TRUE, False, FALSE, y,Y,yes,Yes,YES,n,N,no,No,NO,on,On,ON,off,Off,OFF]: false_bools
bool0: true
bool1: false"""

        val expected = """---
string0: "yes"
string1: "no"
string2: "true"
string3: "false"
string4: "~"
null0: ~
? - true
  - false
: real_bools
? - "True"
  - "TRUE"
  - "False"
  - "FALSE"
  - y
  - Y
  - "yes"
  - "Yes"
  - "YES"
  - n
  - N
  - "no"
  - "No"
  - "NO"
  - "on"
  - "On"
  - "ON"
  - "off"
  - "Off"
  - "OFF"
: false_bools
bool0: true
bool1: false"""

        val docs = YamlLoader.loadFromStr(input)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(expected, writer.toString())
    }

    @Test
    fun testEmptyAndNestedCompact() {
        val s = """---
a:
  b:
    c: hello
  d: {}
e:
  - f
  - g
  - h: []"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(s, writer.toString())
    }

    @Test
    fun testNestedArrays() {
        val s = """---
a:
  - b
  - - c
    - d
    - - e
      - f"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(s, writer.toString())
    }

    @Test
    fun testDeeplyNestedArrays() {
        val s = """---
a:
  - b
  - - c
    - d
    - - e
      - - f
      - - e"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(s, writer.toString())
    }

    @Test
    fun testNestedHashes() {
        val s = """---
a:
  b:
    c:
      d:
        e: f"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]
        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertEquals(s, writer.toString())
    }
}
