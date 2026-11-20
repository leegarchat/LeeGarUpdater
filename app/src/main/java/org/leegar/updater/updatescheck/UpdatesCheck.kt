/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.leegar.updater.updatescheck

sealed interface UpdatesCheckState {
    data object Idle : UpdatesCheckState
    data object Checking : UpdatesCheckState
    data object NoInternet : UpdatesCheckState
    data object Error : UpdatesCheckState
}
