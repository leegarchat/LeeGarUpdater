/*
 * SPDX-FileCopyrightText: LeeGar Updater
 * SPDX-License-Identifier: Apache-2.0
 */

package org.leegar.updater.data

/** Maintainer and support links the OTA server publishes for the device. */
data class SupportInfo(
    val maintainer: String? = null,
    val forum: String? = null,
    val firmware: String? = null,
    val paypal: String? = null,
)
