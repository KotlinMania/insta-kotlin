// port-lint: tests content/json.rs
package io.github.kotlinmania.insta.content

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {
    @Test
    fun testToString() {
        val json = toString(
            Content.Map(
                listOf(
                    Content.Entry(
                        Content.from("environments"),
                        Content.Seq(
                            listOf(
                                Content.from("development"),
                                Content.from("production"),
                            ),
                        ),
                    ),
                    Content.Entry(Content.from("cmdline"), Content.Seq(emptyList())),
                    Content.Entry(Content.from("extra"), Content.Map(emptyList())),
                ),
            ),
        )
        assertEquals("""{"environments":["development","production"],"cmdline":[],"extra":{}}""", json)
    }

    @Test
    fun testToStringPretty() {
        val json = toStringPretty(
            Content.Map(
                listOf(
                    Content.Entry(
                        Content.from("environments"),
                        Content.Seq(
                            listOf(
                                Content.from("development"),
                                Content.from("production"),
                            ),
                        ),
                    ),
                    Content.Entry(Content.from("cmdline"), Content.Seq(emptyList())),
                    Content.Entry(Content.from("extra"), Content.Map(emptyList())),
                ),
            ),
        )
        val expected = """{
  "environments": [
    "development",
    "production"
  ],
  "cmdline": [],
  "extra": {}
}"""
        assertEquals(expected, json)
    }

    @Test
    fun testToStringNumKeys() {
        val content = Content.Map(
            listOf(
                Content.Entry(Content.from(42u), Content.from(true)),
                Content.Entry(Content.from(-23), Content.from(false)),
            ),
        )
        val json = toStringPretty(content)
        val expected = """{
  "42": true,
  "-23": false
}"""
        assertEquals(expected, json)
    }

    @Test
    fun testToStringPrettyComplex() {
        val content = Content.Map(
            listOf(
                Content.Entry(
                    Content.from("is_alive"),
                    Content.NewtypeStruct("Some", Content.from(true)),
                ),
                Content.Entry(
                    Content.from("newtype_variant"),
                    Content.NewtypeVariant(
                        "Foo",
                        0u,
                        "variant_a",
                        Content.Struct(
                            "VariantA",
                            listOf(
                                Content.Field("field_a", Content.from("value_a")),
                                Content.Field("field_b", Content.from(42u)),
                            ),
                        ),
                    ),
                ),
                Content.Entry(
                    Content.from("struct_variant"),
                    Content.StructVariant(
                        "Foo",
                        0u,
                        "variant_b",
                        listOf(
                            Content.Field("field_a", Content.from("value_a")),
                            Content.Field("field_b", Content.from(42u)),
                        ),
                    ),
                ),
                Content.Entry(
                    Content.from("tuple_variant"),
                    Content.TupleVariant(
                        "Foo",
                        0u,
                        "variant_c",
                        listOf(Content.from("value_a"), Content.from(42u)),
                    ),
                ),
                Content.Entry(Content.from("empty_array"), Content.Seq(emptyList())),
                Content.Entry(Content.from("empty_object"), Content.Map(emptyList())),
                Content.Entry(Content.from("array"), Content.Seq(listOf(Content.from(true)))),
                Content.Entry(
                    Content.from("object"),
                    Content.Map(listOf(Content.Entry(Content.from("foo"), Content.from(true)))),
                ),
                Content.Entry(
                    Content.from("array_of_objects"),
                    Content.Seq(
                        listOf(
                            Content.Struct(
                                "MyType",
                                listOf(
                                    Content.Field("foo", Content.from("bar")),
                                    Content.Field("bar", Content.from("xxx")),
                                ),
                            ),
                        ),
                    ),
                ),
                Content.Entry(
                    Content.from("unit_variant"),
                    Content.UnitVariant("Stuff", 0u, "value"),
                ),
                Content.Entry(Content.from("u8"), Content.from(8u.toUByte())),
                Content.Entry(Content.from("u16"), Content.from(16u.toUShort())),
                Content.Entry(Content.from("u32"), Content.from(32u)),
                Content.Entry(Content.from("u64"), Content.from(64uL)),
                Content.Entry(Content.from("u128"), Content.from(UInt128.parse("128"))),
                Content.Entry(Content.from("i8"), Content.from(8.toByte())),
                Content.Entry(Content.from("i16"), Content.from(16.toShort())),
                Content.Entry(Content.from("i32"), Content.from(32)),
                Content.Entry(Content.from("i64"), Content.from(64L)),
                Content.Entry(Content.from("i128"), Content.from(Int128.parse("128"))),
                Content.Entry(Content.from("f32"), Content.from(32.0f)),
                Content.Entry(Content.from("f64"), Content.from(64.0)),
                Content.Entry(Content.from("char"), Content.fromChar('A')),
                Content.Entry(Content.from("bytes"), Content.from("hehe".encodeToByteArray())),
                Content.Entry(Content.from("null"), Content.None),
                Content.Entry(Content.from("unit"), Content.UnitValue),
                Content.Entry(
                    Content.from("crazy_string"),
                    Content.from((0..126).map { it.toChar() }.joinToString("")),
                ),
            ),
        )
        val json = toStringPretty(content)
        val expected = """{
  "is_alive": true,
  "newtype_variant": {
    "variant_a": {
      "field_a": "value_a",
      "field_b": 42
    }
  },
  "struct_variant": {
    "variant_b": {
      "field_a": "value_a",
      "field_b": 42
    }
  },
  "tuple_variant": {
    "variant_c": [
      "value_a",
      42
    ]
  },
  "empty_array": [],
  "empty_object": {},
  "array": [
    true
  ],
  "object": {
    "foo": true
  },
  "array_of_objects": [
    {
      "foo": "bar",
      "bar": "xxx"
    }
  ],
  "unit_variant": "value",
  "u8": 8,
  "u16": 16,
  "u32": 32,
  "u64": 64,
  "u128": 128,
  "i8": 8,
  "i16": 16,
  "i32": 32,
  "i64": 64,
  "i128": 128,
  "f32": 32.0,
  "f64": 64.0,
  "char": "A",
  "bytes": [
    104,
    101,
    104,
    101
  ],
  "null": null,
  "unit": null,
  "crazy_string": "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f !\"#${'$'}%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
}"""
        assertEquals(expected, json)
    }
}
