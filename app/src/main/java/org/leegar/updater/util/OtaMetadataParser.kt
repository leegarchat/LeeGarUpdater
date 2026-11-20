/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.leegar.updater.util

import android.content.Context
import android.ota.nano.OtaPackageMetadata.OtaMetadata
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.leegar.updater.R

class OtaMetadataParser @Throws(IOException::class) constructor(file: File, context: Context) {
    val sdkLevel: Int
    val securityPatchLevel: String
    val timestamp: Long
    val isABUpdate: Boolean

    init {
        val metadata = ZipFile(file).use { readMetadata(it, context) }
        isABUpdate = when (metadata.type) {
            OtaMetadata.AB -> true
            OtaMetadata.BLOCK -> false
            // OtaMetadata.BRICK -> false
            else -> throw IOException(context.getString(R.string.err_unsupported_ota_type, metadata.type))
        }

        val postcondition = metadata.postcondition
        sdkLevel = postcondition.sdkLevel.toInt()
        securityPatchLevel = postcondition.securityPatchLevel
        timestamp = postcondition.timestamp
    }

    companion object {
        private const val METADATA_PROTO_NAME = "META-INF/com/android/metadata.pb"

        @Throws(IOException::class)
        private fun readMetadata(zipFile: ZipFile, context: Context): OtaMetadata {
            val entry = zipFile.getEntry(METADATA_PROTO_NAME)
                ?: throw IOException(context.getString(R.string.err_metadata_not_found, METADATA_PROTO_NAME, zipFile.name))

            return zipFile.getInputStream(entry).use { input ->
                OtaMetadata.parseFrom(input.readBytes())
            }
        }
    }
}
