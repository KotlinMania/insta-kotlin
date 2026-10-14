package io.github.kotlinmania.insta.content.yaml

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.ContentError
import io.github.kotlinmania.insta.content.yaml.vendored.Yaml
import io.github.kotlinmania.insta.content.yaml.vendored.YamlEmitter
import io.github.kotlinmania.insta.content.yaml.vendored.YamlHashEntry
import io.github.kotlinmania.insta.content.yaml.vendored.YamlLoader

fun parseStr(
    s: kotlin.String,
    filename: kotlin.String,
): Content {
    val blobs =
        YamlLoader.loadFromStr(s)
            ?: throw IllegalArgumentException(ContentError.FailedParsingYaml(filename).toString())

    if (blobs.size != 1) {
        throw IllegalArgumentException(ContentError.FailedParsingYaml(filename).toString())
    }
    return fromYamlBlob(blobs[0], filename)
}

private fun fromYamlBlob(
    blob: Yaml,
    filename: kotlin.String,
): Content =
    when (blob) {
        is Yaml.Null -> {
            Content.None
        }

        is Yaml.BooleanValue -> {
            Content.from(blob.value)
        }

        is Yaml.Integer -> {
            Content.from(blob.value)
        }

        is Yaml.Real -> {
            val real =
                blob.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException(ContentError.FailedParsingYaml(filename).toString())
            Content.from(real)
        }

        is Yaml.StringValue -> {
            Content.from(blob.value)
        }

        is Yaml.Array -> {
            Content.Seq(blob.value.map { fromYamlBlob(it, filename) })
        }

        is Yaml.Hash -> {
            Content.Map(
                blob.value.map { entry ->
                    Content.Entry(
                        fromYamlBlob(entry.key, filename),
                        fromYamlBlob(entry.value, filename),
                    )
                },
            )
        }

        is Yaml.BadValue -> {
            throw IllegalArgumentException(ContentError.FailedParsingYaml(filename).toString())
        }
    }

fun toString(content: Content): kotlin.String {
    val yamlBlob = toYamlValue(content)

    val buf = StringBuilder()
    val emitter = YamlEmitter.new(buf)
    emitter.dump(yamlBlob)

    if (!buf.endsWith('\n')) {
        buf.append('\n')
    }
    return buf.toString()
}

private fun translateSeq(seq: List<Content>): Yaml =
    Yaml.Array(seq.map { toYamlValue(it) })

private fun translateFields(fields: List<Content.Field>): Yaml =
    Yaml.Hash(
        fields.map { field ->
            YamlHashEntry(Yaml.StringValue(field.name), toYamlValue(field.value))
        },
    )

private fun toYamlValue(content: Content): Yaml =
    when (content) {
        is Content.Bool -> {
            Yaml.BooleanValue(content.value)
        }

        is Content.U8 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.U16 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.U32 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.U64 -> {
            Yaml.Real(content.value.toString())
        }

        is Content.U128 -> {
            Yaml.Real(content.value.toString())
        }

        is Content.I8 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.I16 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.I32 -> {
            Yaml.Integer(content.value.toLong())
        }

        is Content.I64 -> {
            Yaml.Integer(content.value)
        }

        is Content.I128 -> {
            Yaml.Real(content.value.toString())
        }

        is Content.F32 -> {
            Yaml.Real(content.value.toString())
        }

        is Content.F64 -> {
            Yaml.Real(content.value.toString())
        }

        is Content.Char -> {
            Yaml.StringValue(content.value.toString())
        }

        is Content.Str -> {
            Yaml.StringValue(content.value)
        }

        is Content.Bytes -> {
            Yaml.Array(content.value.map { b -> Yaml.Integer((b.toInt() and 0xFF).toLong()) })
        }

        Content.None, Content.UnitValue, is Content.UnitStruct -> {
            Yaml.Null
        }

        is Content.Some -> {
            toYamlValue(content.value)
        }

        is Content.UnitVariant -> {
            Yaml.StringValue(content.variant)
        }

        is Content.NewtypeStruct -> {
            toYamlValue(content.value)
        }

        is Content.NewtypeVariant -> {
            Yaml.Hash(
                listOf(
                    YamlHashEntry(Yaml.StringValue(content.variant), toYamlValue(content.value)),
                ),
            )
        }

        is Content.Seq -> {
            translateSeq(content.value)
        }

        is Content.Tuple -> {
            translateSeq(content.value)
        }

        is Content.TupleStruct -> {
            translateSeq(content.value)
        }

        is Content.TupleVariant -> {
            Yaml.Hash(
                listOf(
                    YamlHashEntry(Yaml.StringValue(content.variant), translateSeq(content.value)),
                ),
            )
        }

        is Content.Map -> {
            Yaml.Hash(
                content.value.map { entry ->
                    YamlHashEntry(toYamlValue(entry.key), toYamlValue(entry.value))
                },
            )
        }

        is Content.Struct -> {
            translateFields(content.fields)
        }

        is Content.StructVariant -> {
            Yaml.Hash(
                listOf(
                    YamlHashEntry(Yaml.StringValue(content.variant), translateFields(content.fields)),
                ),
            )
        }
    }
