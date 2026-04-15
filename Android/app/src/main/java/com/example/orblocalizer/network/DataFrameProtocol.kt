package com.example.orblocalizer.network

import com.example.orblocalizer.sensor.IMUData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Binary data frame protocol for Android <-> Ubuntu communication.
 *
 * Frame format:
 * [Header:4B][Type:1B][Timestamp:8B][Size:4B][Payload:NB][Checksum:4B]
 *  0xDEADBEEF  0x01-04  Unix微秒时间   字节数    实际数据   CRC32
 *
 * Data types:
 * 0x01 - Camera Frame (YUV420 or H.264)
 * 0x02 - IMU Data (9-DOF)
 * 0x03 - Pose (6-DOF position + rotation)
 * 0x04 - Map Data (point cloud)
 */
object DataFrameProtocol {

    private const val HEADER_MAGIC = 0xDEADBEEF.toInt()
    const val TYPE_CAMERA = 0x01.toByte()
    const val TYPE_IMU = 0x02.toByte()
    const val TYPE_POSE = 0x03.toByte()
    const val TYPE_MAP = 0x04.toByte()

    private const val HEADER_SIZE = 4   // Magic
    private const val TYPE_SIZE = 1     // Type
    private const val TIMESTAMP_SIZE = 8 // Timestamp
    private const val PAYLOAD_SIZE_FIELD = 4 // Payload length field
    private const val CHECKSUM_SIZE = 4  // CRC32
    const val FRAME_OVERHEAD = HEADER_SIZE + TYPE_SIZE + TIMESTAMP_SIZE + PAYLOAD_SIZE_FIELD + CHECKSUM_SIZE

    fun createCameraFrame(yuvData: ByteArray, timestampUs: Long): ByteArray {
        return buildFrame(TYPE_CAMERA, timestampUs, yuvData)
    }

    fun createIMUFrame(imuData: IMUData): ByteArray {
        val payload = ByteBuffer.allocate(9 * 4)  // 9 floats * 4 bytes
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(imuData.accelX)
            .putFloat(imuData.accelY)
            .putFloat(imuData.accelZ)
            .putFloat(imuData.gyroX)
            .putFloat(imuData.gyroY)
            .putFloat(imuData.gyroZ)
            .putFloat(imuData.magX)
            .putFloat(imuData.magY)
            .putFloat(imuData.magZ)
            .array()
        return buildFrame(TYPE_IMU, imuData.timestamp, payload)
    }

    fun createPoseFrame(
        timestampUs: Long,
        x: Float, y: Float, z: Float,
        qx: Float, qy: Float, qz: Float, qw: Float
    ): ByteArray {
        val payload = ByteBuffer.allocate(7 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(x)
            .putFloat(y)
            .putFloat(z)
            .putFloat(qx)
            .putFloat(qy)
            .putFloat(qz)
            .putFloat(qw)
            .array()
        return buildFrame(TYPE_POSE, timestampUs, payload)
    }

    private fun buildFrame(type: Byte, timestampUs: Long, payload: ByteArray): ByteArray {
        val totalSize = FRAME_OVERHEAD + payload.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(HEADER_MAGIC)
        buffer.put(type)
        buffer.putLong(timestampUs)
        buffer.putInt(payload.size)
        buffer.put(payload)

        val crc = CRC32()
        crc.update(buffer.array(), 0, totalSize - CHECKSUM_SIZE)
        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }

    fun parseFrame(data: ByteArray): ParsedFrame? {
        if (data.size < FRAME_OVERHEAD) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        if (magic != HEADER_MAGIC) return null

        val type = buffer.get()
        val timestamp = buffer.long
        val payloadSize = buffer.int

        if (data.size < FRAME_OVERHEAD + payloadSize) return null

        val payload = ByteArray(payloadSize)
        buffer.get(payload)

        val receivedChecksum = buffer.int
        val crc = CRC32()
        crc.update(data, 0, data.size - CHECKSUM_SIZE)
        val expectedChecksum = crc.value.toInt()

        if (receivedChecksum != expectedChecksum) return null

        return ParsedFrame(type, timestamp, payload)
    }

    data class ParsedFrame(
        val type: Byte,
        val timestampUs: Long,
        val payload: ByteArray
    )
}
