package com.example.ui.map.mvt

import com.example.model.GeoPoint
import java.io.ByteArrayOutputStream
import kotlin.math.*

/**
 * Pure-Kotlin Mapbox Vector Tile (MVT v2.1) binary Protobuf parser and encoder.
 * Implements `vector_tile.proto` wire format, zigzag delta coordinate decoding,
 * geometry commands (MoveTo, LineTo, ClosePath), and Web Mercator tile-to-WGS84 projection.
 */
enum class MvtGeometryType(val protoValue: Int) {
    UNKNOWN(0),
    POINT(1),
    LINESTRING(2),
    POLYGON(3);

    companion object {
        fun fromProto(value: Int): MvtGeometryType =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

data class MvtTileCoord(val x: Int, val y: Int)

data class MvtFeature(
    val id: Long,
    val type: MvtGeometryType,
    val properties: Map<String, Any?>,
    val tileGeometries: List<List<MvtTileCoord>>,
    val geoGeometries: List<List<GeoPoint>>
)

data class MvtLayer(
    val name: String,
    val version: Int,
    val extent: Int,
    val features: List<MvtFeature>
)

data class MvtTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val layers: List<MvtLayer>
) {
    val totalFeatures: Int get() = layers.sumOf { it.features.size }
}

/**
 * Input models used by [MvtEncoder] when compiling GeoJSON or local tactical geometry into MVT .pbf bytes.
 */
data class MvtInputFeature(
    val id: Long,
    val type: MvtGeometryType,
    val properties: Map<String, Any?>,
    val geoRingsOrLines: List<List<GeoPoint>>
)

data class MvtInputLayer(
    val name: String,
    val extent: Int = 4096,
    val features: List<MvtInputFeature>
)

object MvtMercator {
    fun latLngToTileLocal(
        lat: Double,
        lng: Double,
        tileZ: Int,
        tileX: Int,
        tileY: Int,
        extent: Int = 4096
    ): MvtTileCoord {
        val n = 2.0.pow(tileZ)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val globalX = ((lng + 180.0) / 360.0) * n
        val globalY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n
        val tx = ((globalX - tileX) * extent).roundToInt()
        val ty = ((globalY - tileY) * extent).roundToInt()
        return MvtTileCoord(tx, ty)
    }

    fun tileLocalToLatLng(
        tx: Int,
        ty: Int,
        tileZ: Int,
        tileX: Int,
        tileY: Int,
        extent: Int = 4096
    ): GeoPoint {
        val n = 2.0.pow(tileZ)
        val globalX = tileX + (tx.toDouble() / extent)
        val globalY = tileY + (ty.toDouble() / extent)
        val lng = (globalX / n) * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * globalY / n)))
        val lat = Math.toDegrees(latRad).coerceIn(-85.05112878, 85.05112878)
        return GeoPoint(lat = lat, lng = lng)
    }

    fun latLngToTileIndex(lat: Double, lng: Double, tileZ: Int): Pair<Int, Int> {
        val n = 2.0.pow(tileZ)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val x = floor(((lng + 180.0) / 360.0) * n).toInt().coerceIn(0, n.toInt() - 1)
        val y = floor(((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n)
            .toInt().coerceIn(0, n.toInt() - 1)
        return x to y
    }
}

object MvtParser {
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private const val CMD_MOVE_TO = 1
    private const val CMD_LINE_TO = 2
    private const val CMD_CLOSE_PATH = 7

    fun parse(bytes: ByteArray, tileZ: Int, tileX: Int, tileY: Int): MvtTile {
        val reader = ProtoReader(bytes, 0, bytes.size)
        val layers = mutableListOf<MvtLayer>()

        while (reader.hasRemaining()) {
            val tag = reader.readVarint32()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            if (fieldNumber == 3 && wireType == WIRE_LENGTH_DELIMITED) {
                val len = reader.readVarint32()
                val layerBytes = reader.readBytesSlice(len)
                layers.add(parseLayer(layerBytes, tileZ, tileX, tileY))
            } else {
                reader.skipField(wireType)
            }
        }
        return MvtTile(z = tileZ, x = tileX, y = tileY, layers = layers)
    }

    private fun parseLayer(bytes: ByteArray, tileZ: Int, tileX: Int, tileY: Int): MvtLayer {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var name = ""
        var version = 1
        var extent = 4096
        val keys = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        val rawFeatures = mutableListOf<RawFeature>()

        while (reader.hasRemaining()) {
            val tag = reader.readVarint32()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> if (wireType == WIRE_LENGTH_DELIMITED) name = reader.readString() else reader.skipField(wireType)
                2 -> if (wireType == WIRE_LENGTH_DELIMITED) {
                    val len = reader.readVarint32()
                    rawFeatures.add(parseRawFeature(reader.readBytesSlice(len)))
                } else {
                    reader.skipField(wireType)
                }
                3 -> if (wireType == WIRE_LENGTH_DELIMITED) keys.add(reader.readString()) else reader.skipField(wireType)
                4 -> if (wireType == WIRE_LENGTH_DELIMITED) {
                    val len = reader.readVarint32()
                    values.add(parseValue(reader.readBytesSlice(len)))
                } else {
                    reader.skipField(wireType)
                }
                5 -> if (wireType == WIRE_VARINT) extent = reader.readVarint32().coerceAtLeast(1) else reader.skipField(wireType)
                15 -> if (wireType == WIRE_VARINT) version = reader.readVarint32() else reader.skipField(wireType)
                else -> reader.skipField(wireType)
            }
        }

        val features = rawFeatures.map { raw ->
            val props = LinkedHashMap<String, Any?>()
            var i = 0
            while (i + 1 < raw.tags.size) {
                val kIdx = raw.tags[i]
                val vIdx = raw.tags[i + 1]
                if (kIdx in keys.indices && vIdx in values.indices) {
                    props[keys[kIdx]] = values[vIdx]
                }
                i += 2
            }

            val tileGeoms = decodeGeometryCommands(raw.type, raw.geometry)
            val geoGeoms = tileGeoms.map { ring ->
                ring.map { coord ->
                    MvtMercator.tileLocalToLatLng(coord.x, coord.y, tileZ, tileX, tileY, extent)
                }
            }

            MvtFeature(
                id = raw.id,
                type = raw.type,
                properties = props,
                tileGeometries = tileGeoms,
                geoGeometries = geoGeoms
            )
        }

        return MvtLayer(name = name, version = version, extent = extent, features = features)
    }

    private data class RawFeature(
        val id: Long,
        val tags: List<Int>,
        val type: MvtGeometryType,
        val geometry: List<Int>
    )

    private fun parseRawFeature(bytes: ByteArray): RawFeature {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var id = 0L
        var type = MvtGeometryType.UNKNOWN
        val tags = mutableListOf<Int>()
        val geometry = mutableListOf<Int>()

        while (reader.hasRemaining()) {
            val tag = reader.readVarint32()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7

            when (fieldNumber) {
                1 -> if (wireType == WIRE_VARINT) id = reader.readVarint64() else reader.skipField(wireType)
                2 -> {
                    if (wireType == WIRE_LENGTH_DELIMITED) {
                        val len = reader.readVarint32().coerceAtLeast(0)
                        val slice = reader.readBytesSlice(len)
                        val packedReader = ProtoReader(slice, 0, slice.size)
                        while (packedReader.hasRemaining()) tags.add(packedReader.readVarint32())
                    } else if (wireType == WIRE_VARINT) {
                        tags.add(reader.readVarint32())
                    } else {
                        reader.skipField(wireType)
                    }
                }
                3 -> if (wireType == WIRE_VARINT) type = MvtGeometryType.fromProto(reader.readVarint32()) else reader.skipField(wireType)
                4 -> {
                    if (wireType == WIRE_LENGTH_DELIMITED) {
                        val len = reader.readVarint32().coerceAtLeast(0)
                        val slice = reader.readBytesSlice(len)
                        val packedReader = ProtoReader(slice, 0, slice.size)
                        while (packedReader.hasRemaining()) geometry.add(packedReader.readVarint32())
                    } else if (wireType == WIRE_VARINT) {
                        geometry.add(reader.readVarint32())
                    } else {
                        reader.skipField(wireType)
                    }
                }
                else -> reader.skipField(wireType)
            }
        }
        return RawFeature(id = id, tags = tags, type = type, geometry = geometry)
    }

    private fun parseValue(bytes: ByteArray): Any? {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var result: Any? = null
        while (reader.hasRemaining()) {
            val tag = reader.readVarint32()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> if (wireType == WIRE_LENGTH_DELIMITED) result = reader.readString() else reader.skipField(wireType)
                2 -> if (wireType == WIRE_FIXED32) result = java.lang.Float.intBitsToFloat(reader.readFixed32()) else reader.skipField(wireType)
                3 -> if (wireType == WIRE_FIXED64) result = java.lang.Double.longBitsToDouble(reader.readFixed64()) else reader.skipField(wireType)
                4 -> if (wireType == WIRE_VARINT) result = reader.readVarint64() else reader.skipField(wireType)
                5 -> if (wireType == WIRE_VARINT) result = reader.readVarint64() else reader.skipField(wireType)
                6 -> if (wireType == WIRE_VARINT) result = decodeZigZag64(reader.readVarint64()) else reader.skipField(wireType)
                7 -> if (wireType == WIRE_VARINT) result = reader.readVarint64() != 0L else reader.skipField(wireType)
                else -> reader.skipField(wireType)
            }
        }
        return result
    }

    internal fun decodeGeometryCommands(
        type: MvtGeometryType,
        commands: List<Int>
    ): List<List<MvtTileCoord>> {
        val parts = mutableListOf<List<MvtTileCoord>>()
        var currentPart = mutableListOf<MvtTileCoord>()
        var cursorX = 0
        var cursorY = 0
        var idx = 0

        while (idx < commands.size) {
            val cmdInt = commands[idx++]
            val cmdId = cmdInt and 0x7
            val cmdCount = cmdInt ushr 3

            when (cmdId) {
                CMD_MOVE_TO -> {
                    for (step in 0 until cmdCount) {
                        if (idx + 1 >= commands.size) break
                        cursorX += decodeZigZag32(commands[idx++])
                        cursorY += decodeZigZag32(commands[idx++])
                        if (type == MvtGeometryType.POINT) {
                            currentPart.add(MvtTileCoord(cursorX, cursorY))
                        } else {
                            if (currentPart.isNotEmpty()) {
                                parts.add(currentPart)
                                currentPart = mutableListOf()
                            }
                            currentPart.add(MvtTileCoord(cursorX, cursorY))
                        }
                    }
                }
                CMD_LINE_TO -> {
                    for (step in 0 until cmdCount) {
                        if (idx + 1 >= commands.size) break
                        cursorX += decodeZigZag32(commands[idx++])
                        cursorY += decodeZigZag32(commands[idx++])
                        currentPart.add(MvtTileCoord(cursorX, cursorY))
                    }
                }
                CMD_CLOSE_PATH -> {
                    if (currentPart.isNotEmpty()) {
                        val first = currentPart.first()
                        val last = currentPart.last()
                        if (first != last) {
                            currentPart.add(first)
                        }
                        parts.add(currentPart)
                        currentPart = mutableListOf()
                    }
                }
                else -> break
            }
        }

        if (currentPart.isNotEmpty()) {
            parts.add(currentPart)
        }
        return parts
    }

    private fun decodeZigZag32(n: Int): Int = (n ushr 1) xor -(n and 1)
    private fun decodeZigZag64(n: Long): Long = (n ushr 1) xor -(n and 1L)

    private class ProtoReader(
        private val data: ByteArray,
        start: Int,
        private val end: Int
    ) {
        var pos: Int = start

        fun hasRemaining(): Boolean = pos < end

        fun readVarint32(): Int = readVarint64().toInt()

        fun readVarint64(): Long {
            var shift = 0
            var result = 0L
            while (pos < end) {
                val b = data[pos++].toInt()
                result = result or ((b.toLong() and 0x7FL) shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift >= 64) break
            }
            return result
        }

        fun readFixed32(): Int {
            if (pos + 4 > end) {
                pos = end
                return 0
            }
            val b0 = data[pos++].toInt() and 0xFF
            val b1 = data[pos++].toInt() and 0xFF
            val b2 = data[pos++].toInt() and 0xFF
            val b3 = data[pos++].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun readFixed64(): Long {
            if (pos + 8 > end) {
                pos = end
                return 0L
            }
            var result = 0L
            for (i in 0 until 8) {
                result = result or ((data[pos++].toLong() and 0xFFL) shl (i * 8))
            }
            return result
        }

        fun readBytesSlice(length: Int): ByteArray {
            val safeLen = length.coerceIn(0, end - pos)
            val slice = data.copyOfRange(pos, pos + safeLen)
            pos += safeLen
            return slice
        }

        fun readString(): String {
            val len = readVarint32()
            val safeLen = len.coerceIn(0, end - pos)
            val str = String(data, pos, safeLen, Charsets.UTF_8)
            pos += safeLen
            return str
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint64()
                WIRE_FIXED64 -> pos = (pos + 8).coerceAtMost(end)
                WIRE_LENGTH_DELIMITED -> {
                    val len = readVarint32().coerceAtLeast(0)
                    pos = (pos + len).coerceAtMost(end)
                }
                WIRE_FIXED32 -> pos = (pos + 4).coerceAtMost(end)
                else -> pos = end
            }
        }
    }
}

/**
 * Encodes local vector features (such as parsed GeoJSON overlays) into spec-compliant
 * Mapbox Vector Tile v2.1 binary protobuf bytes.
 */
object MvtEncoder {
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2

    private const val CMD_MOVE_TO = 1
    private const val CMD_LINE_TO = 2
    private const val CMD_CLOSE_PATH = 7

    fun encodeTile(
        layers: List<MvtInputLayer>,
        tileZ: Int,
        tileX: Int,
        tileY: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        for (layer in layers) {
            val layerBytes = encodeLayer(layer, tileZ, tileX, tileY)
            writeTag(out, 3, WIRE_LENGTH_DELIMITED)
            writeVarint(out, layerBytes.size.toLong())
            out.write(layerBytes)
        }
        return out.toByteArray()
    }

    private fun encodeLayer(
        layer: MvtInputLayer,
        tileZ: Int,
        tileX: Int,
        tileY: Int
    ): ByteArray {
        val keys = mutableListOf<String>()
        val keyIndexMap = mutableMapOf<String, Int>()
        val values = mutableListOf<Any>()
        val valueIndexMap = mutableMapOf<String, Int>()

        val out = ByteArrayOutputStream()

        // Field 15: version = 2
        writeTag(out, 15, WIRE_VARINT)
        writeVarint(out, 2L)

        // Field 1: name
        writeTag(out, 1, WIRE_LENGTH_DELIMITED)
        writeString(out, layer.name)

        // Field 5: extent
        writeTag(out, 5, WIRE_VARINT)
        writeVarint(out, layer.extent.toLong())

        // Field 2: features
        for (feature in layer.features) {
            val tagIndices = mutableListOf<Int>()
            for ((k, v) in feature.properties) {
                if (v == null) continue
                val kIdx = keyIndexMap.getOrPut(k) {
                    keys.add(k)
                    keys.lastIndex
                }
                val vKey = "${v::class.qualifiedName}:$v"
                val vIdx = valueIndexMap.getOrPut(vKey) {
                    values.add(v)
                    values.lastIndex
                }
                tagIndices.add(kIdx)
                tagIndices.add(vIdx)
            }

            val tileRings = feature.geoRingsOrLines.map { ring ->
                ring.map { pt ->
                    MvtMercator.latLngToTileLocal(pt.lat, pt.lng, tileZ, tileX, tileY, layer.extent)
                }
            }
            val commands = encodeGeometryCommands(feature.type, tileRings)
            val featureBytes = encodeFeature(feature.id, feature.type, tagIndices, commands)

            writeTag(out, 2, WIRE_LENGTH_DELIMITED)
            writeVarint(out, featureBytes.size.toLong())
            out.write(featureBytes)
        }

        // Field 3: keys
        for (key in keys) {
            writeTag(out, 3, WIRE_LENGTH_DELIMITED)
            writeString(out, key)
        }

        // Field 4: values
        for (value in values) {
            val valBytes = encodeValue(value)
            writeTag(out, 4, WIRE_LENGTH_DELIMITED)
            writeVarint(out, valBytes.size.toLong())
            out.write(valBytes)
        }

        return out.toByteArray()
    }

    private fun encodeFeature(
        id: Long,
        type: MvtGeometryType,
        tags: List<Int>,
        geometry: List<Int>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, WIRE_VARINT)
        writeVarint(out, id)

        if (tags.isNotEmpty()) {
            val packed = ByteArrayOutputStream()
            for (t in tags) writeVarint(packed, t.toLong())
            val bytes = packed.toByteArray()
            writeTag(out, 2, WIRE_LENGTH_DELIMITED)
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }

        writeTag(out, 3, WIRE_VARINT)
        writeVarint(out, type.protoValue.toLong())

        if (geometry.isNotEmpty()) {
            val packed = ByteArrayOutputStream()
            for (g in geometry) writeVarint(packed, g.toLong() and 0xFFFFFFFFL)
            val bytes = packed.toByteArray()
            writeTag(out, 4, WIRE_LENGTH_DELIMITED)
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun encodeValue(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        when (value) {
            is String -> {
                writeTag(out, 1, WIRE_LENGTH_DELIMITED)
                writeString(out, value)
            }
            is Boolean -> {
                writeTag(out, 7, WIRE_VARINT)
                writeVarint(out, if (value) 1L else 0L)
            }
            is Int -> {
                writeTag(out, 6, WIRE_VARINT)
                writeVarint(out, encodeZigZag64(value.toLong()))
            }
            is Long -> {
                writeTag(out, 6, WIRE_VARINT)
                writeVarint(out, encodeZigZag64(value))
            }
            is Double -> {
                writeTag(out, 3, WIRE_FIXED64)
                writeFixed64(out, java.lang.Double.doubleToRawLongBits(value))
            }
            is Float -> {
                writeTag(out, 3, WIRE_FIXED64)
                writeFixed64(out, java.lang.Double.doubleToRawLongBits(value.toDouble()))
            }
            else -> {
                writeTag(out, 1, WIRE_LENGTH_DELIMITED)
                writeString(out, value.toString())
            }
        }
        return out.toByteArray()
    }

    private fun encodeGeometryCommands(
        type: MvtGeometryType,
        parts: List<List<MvtTileCoord>>
    ): List<Int> {
        val cmds = mutableListOf<Int>()
        var cursorX = 0
        var cursorY = 0

        when (type) {
            MvtGeometryType.POINT -> {
                val allPoints = parts.flatten()
                if (allPoints.isEmpty()) return cmds
                cmds.add(commandInt(CMD_MOVE_TO, allPoints.size))
                for (pt in allPoints) {
                    cmds.add(encodeZigZag32(pt.x - cursorX))
                    cmds.add(encodeZigZag32(pt.y - cursorY))
                    cursorX = pt.x
                    cursorY = pt.y
                }
            }
            MvtGeometryType.LINESTRING -> {
                for (line in parts) {
                    if (line.size < 2) continue
                    cmds.add(commandInt(CMD_MOVE_TO, 1))
                    cmds.add(encodeZigZag32(line[0].x - cursorX))
                    cmds.add(encodeZigZag32(line[0].y - cursorY))
                    cursorX = line[0].x
                    cursorY = line[0].y

                    cmds.add(commandInt(CMD_LINE_TO, line.size - 1))
                    for (i in 1 until line.size) {
                        cmds.add(encodeZigZag32(line[i].x - cursorX))
                        cmds.add(encodeZigZag32(line[i].y - cursorY))
                        cursorX = line[i].x
                        cursorY = line[i].y
                    }
                }
            }
            MvtGeometryType.POLYGON -> {
                for (rawRing in parts) {
                    if (rawRing.size < 3) continue
                    val ring = if (rawRing.first() == rawRing.last() && rawRing.size >= 4) {
                        rawRing.dropLast(1)
                    } else {
                        rawRing
                    }
                    if (ring.size < 3) continue

                    cmds.add(commandInt(CMD_MOVE_TO, 1))
                    cmds.add(encodeZigZag32(ring[0].x - cursorX))
                    cmds.add(encodeZigZag32(ring[0].y - cursorY))
                    cursorX = ring[0].x
                    cursorY = ring[0].y

                    cmds.add(commandInt(CMD_LINE_TO, ring.size - 1))
                    for (i in 1 until ring.size) {
                        cmds.add(encodeZigZag32(ring[i].x - cursorX))
                        cmds.add(encodeZigZag32(ring[i].y - cursorY))
                        cursorX = ring[i].x
                        cursorY = ring[i].y
                    }
                    cmds.add(commandInt(CMD_CLOSE_PATH, 1))
                }
            }
            MvtGeometryType.UNKNOWN -> Unit
        }
        return cmds
    }

    private fun commandInt(id: Int, count: Int): Int = (id and 0x7) or (count shl 3)
    private fun encodeZigZag32(n: Int): Int = (n shl 1) xor (n shr 31)
    private fun encodeZigZag64(n: Long): Long = (n shl 1) xor (n shr 63)

    private fun writeTag(out: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
        out.write(v.toInt() and 0x7F)
    }

    private fun writeFixed64(out: ByteArrayOutputStream, value: Long) {
        for (i in 0 until 8) {
            out.write(((value ushr (i * 8)) and 0xFFL).toInt())
        }
    }

    private fun writeString(out: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }
}
