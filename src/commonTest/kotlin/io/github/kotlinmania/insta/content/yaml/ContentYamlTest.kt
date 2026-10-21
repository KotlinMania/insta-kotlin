// port-lint: tests content/yaml/mod.rs
package io.github.kotlinmania.insta.content.yaml

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.yaml.vendored.YamlEmitter
import io.github.kotlinmania.insta.content.yaml.vendored.YamlLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentYamlTest {
    @Test
    fun testApi() {
        val s = """
# from yaml-cpp example
- name: Ogre
  position: [0, 5, 0]
  powers:
    - name: Club
      damage: 10
    - name: Fist
      damage: 8
- name: Dragon
  position: [1, 0, 10]
  powers:
    - name: Fire Breath
      damage: 25
    - name: Claws
      damage: 15
- name: Wizard
  position: [5, -3, 0]
  powers:
    - name: Acid Rain
      damage: 50
    - name: Staff
      damage: 3
"""
        val docs = YamlLoader.loadFromStr(s)
        assertNotNull(docs)
        val doc = docs[0]

        assertEquals("Ogre", doc[0]["name"].asStr())

        val writer = StringBuilder()
        val emitter = YamlEmitter.new(writer)
        emitter.dump(doc)

        assertTrue(writer.isNotEmpty())
    }

    @Test
    fun testFail() {
        val s = """
# syntax error
scalar
key: [1, 2]]
key1:a2
"""
        assertNull(YamlLoader.loadFromStr(s))
    }

    @Test
    fun testContentYamlRoundTrip() {
        val content =
            Content.Map(
                listOf(
                    Content.Entry(Content.from("name"), Content.from("Ogre")),
                    Content.Entry(Content.from("powers"), Content.Seq(listOf(Content.from("Club"), Content.from("Fist")))),
                ),
            )
        val yamlStr = toString(content)
        assertTrue(yamlStr.isNotEmpty())
        val parsed = parseStr(yamlStr, "test.yaml")
        assertEquals(content, parsed)
    }
}
